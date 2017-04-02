
(ns symba
  (:use clojure.pprint)
  (:require [symba.types :as t]
            [clojure.string :as string])
  (:import 
    [System.Runtime.InteropServices Marshal]
    [LLVMSharp
     IRBuilder
     LLVM LLVMBool LLVMTypeRef LLVMValueRef
     LLVMCodeGenFileType
     Module ExecutionEngine]
    [Symba Symba]))

(def genstr (comp str gensym str))

(def make-engine
  (memoize (fn [mod] (Symba/CompilerForModule mod))))

(def new-delegate-type
  (memoize
    (fn [[ret args]]
      (Symba/NewConcreteGenericType
        (genstr "delegate")
        ret (into-array args)))))


(defn module
  ([body]
   (module "" body))
  ([name body]
   {::op :module
    ::name name
    ::body body}))

(defn function
  [name return parameters body]
  {::op :function
   ::name name
   ::return return
   ::parameters parameters
   ::body body})

(defn block 
  ([body]
   (block "" body))
  ([name body]
   {::op :block
    ::name name
    ::body body}))

(defn ret [value]
  {::op :ret
   ::value value})

(defn hyphens-to-camels [^String n]
  (-> n
      (string/replace #"^(\w)" #(string/upper-case (second %1)))
      (string/replace #"-(\w)" #(string/upper-case (second %1)))))

(defmacro binary-ir [op]
  `(do
     (def ~(symbol (name op))
       (fn [lhs# rhs#]
         {::op ~op
          ::lhs lhs#
          ::rhs rhs#}))
     (defmethod emit-ir* ~op
       [{:keys [::builder] :as ctx#}
        {:keys [::lhs ::rhs]}]
       (. ~'builder ~(symbol (str "Create" (hyphens-to-camels (name op))))
          (emit-ir* ctx# ~'lhs) (emit-ir* ctx# ~'rhs) ""))))

(defn add [lhs rhs]
  {::op :add
   ::lhs lhs
   ::rhs rhs})

(defn br
  ([dest]
   (br "" dest))
  ([name dest]
   {::op :br
    ::name name
    ::dest dest})
  ([if then else]
   (br "" if then else))
  ([name if then else]
   {::op :cond-br
    ::name name
    ::if if
    ::then then
    ::else else}))

(defn param [i]
  {::op :param
   ::index i})

(defn icmp [pred lhs rhs]
  {::op :icmp
   ::pred pred 
   ::lhs lhs 
   ::rhs rhs})


(defmulti emit* (fn [ctx ir] (::op ir)))
(defmulti emit-ir* (fn [ctx ir] (::op ir)))

(defn emit [ir] (emit* {} ir))

(defmethod emit* :module
  [ctx {:keys [::body ::name]}]
  (let [module (LLVM/ModuleCreateWithName name)
        ctx* (assoc ctx ::module module)]
    (reduce
      (fn [ctx' function]
     
        (merge ctx' (emit* ctx' function)) )
      ctx*
      body)))

(defmethod emit* :function
  [{:keys [::module] :as ctx}
   {:keys [::name ::return ::parameters ::body]}]
  (let [function (LLVM/AddFunction module name (t/function return parameters))
        builder (IRBuilder.)
        ctx* (-> ctx
                 (assoc 
                   ::function function
                   ::builder builder)
                 (update ::functions conj function))]
    (doseq [b body] (emit-ir* ctx* b))
    ctx*))

(defmethod emit-ir* :block
  [{:keys [::function ::builder] :as ctx}
   {:keys [::name ::body]}]
  (let [block (LLVM/AppendBasicBlock function name)
        ctx* (assoc ctx ::block block)]
    (.PositionBuilderAtEnd builder block)
    (doseq [b body] (emit-ir* ctx* b))
    block))

(defmethod emit-ir* :ret
  [{:keys [::builder] :as ctx}
   {:keys [::value]}]
  (.CreateRet builder (emit-ir* ctx value)))

(defmethod emit-ir* :add
  [{:keys [::builder] :as ctx}
   {:keys [::lhs ::rhs]}]
  (.CreateAdd builder (emit-ir* ctx lhs) (emit-ir* ctx rhs) ""))

(defmethod emit-ir* :br
  [{:keys [::builder] :as ctx}
   {:keys [::dest]}]
  (.CreateBr builder (emit-ir* ctx dest)))

(def icmp-predicates
  {:eq LLVMSharp.LLVMIntPredicate/LLVMIntEQ
   :ne LLVMSharp.LLVMIntPredicate/LLVMIntNE
   :ugt LLVMSharp.LLVMIntPredicate/LLVMIntUGT
   :uge LLVMSharp.LLVMIntPredicate/LLVMIntUGE
   :ult LLVMSharp.LLVMIntPredicate/LLVMIntULT
   :ule LLVMSharp.LLVMIntPredicate/LLVMIntULE
   :sgt LLVMSharp.LLVMIntPredicate/LLVMIntSGT
   :sge LLVMSharp.LLVMIntPredicate/LLVMIntSGE
   :slt LLVMSharp.LLVMIntPredicate/LLVMIntSLT
   :sl LLVMSharp.LLVMIntPredicate/LLVMIntSLE})

(defmethod emit-ir* :icmp
  [{:keys [::builder] :as ctx}
   {:keys [::pred ::lhs ::rhs]}]
  (.CreateICmp builder
               (icmp-predicates pred)
               (emit-ir* ctx lhs)
               (emit-ir* ctx rhs) ""))

(defmethod emit-ir* :cond-br
  [{:keys [::builder ::block] :as ctx}
   {:keys [::if ::then ::else]}]
  (let [if-val (emit-ir* ctx if)
        then-block (emit-ir* ctx then)
        else-block (emit-ir* ctx else)]
    (.PositionBuilderAtEnd builder block)
    (.CreateCondBr builder if-val then-block else-block)))

(defmethod emit-ir* :param
  [{:keys [::function] :as ctx}
   {:keys [::index]}]
  (LLVM/GetParam function index))

(defn make-fn [{:keys [::module ::function]}]
  (let [delegate (new-delegate-type [Int32 [Int32 Int32]])]
    (Marshal/GetDelegateForFunctionPointer
      (LLVM/GetPointerToGlobal (make-engine module)
                               function)
      delegate)))


(defn demo [file]
  (let [expr
        `(module
          [(function
             "one-or-the-other"
             t/i32 [t/i32 t/i32]
             [(block
                [(br (icmp :eq
                           (param 1)
                           (param 0))
                     (block [(ret (add (param 0)
                                       (param 1)))])
                     (block [(ret (param 1))]))])])])]
    (println (str "Compiling to " file ":"))
    (pprint expr)
    (-> expr
        eval
        emit
        ::module
        (LLVM/WriteBitcodeToFile (str file)))))