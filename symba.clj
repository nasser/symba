(assembly-load-file "bin/Release/Symba.dll")
(assembly-load-file "bin/Release/LLVMSharp.dll")

(ns symba
  (:import 
    [System.Runtime.InteropServices Marshal]
    [LLVMSharp
     LLVM LLVMBool LLVMTypeRef
     LLVMCodeGenFileType
     Module ExecutionEngine]
    [Symba Symba]))

(def genstr (comp str gensym str))

(def make-engine
  (memoize (fn [mod] (Symba/CompilerForModule mod))))

(def new-delegate-type
  (memoize
    (fn [name ret args]
      (Symba/NewConcreteGenericType
        name
        ret (into-array args)))))

(let [False (LLVMBool. 0)
      mod (LLVM/ModuleCreateWithName "LLVMSharpIntro")
      param-types (into-array [(LLVM/Int32Type) (LLVM/Int32Type)])
      fn-type (Symba/FunctionType (LLVM/Int32Type) param-types 2 False)
      sum (LLVM/AddFunction mod "sum" fn-type)
      entry (LLVM/AppendBasicBlock sum "entry")
      main (LLVM/AddFunction mod "main" fn-type)
      entry2 (LLVM/AppendBasicBlock main "entry")
      builder (LLVM/CreateBuilder)]
  (LLVM/PositionBuilderAtEnd builder entry)
  (LLVM/BuildRet builder
                 (LLVM/BuildAdd builder
                                (LLVM/GetParam sum 0)
                                (LLVM/BuildAdd builder
                                               (LLVM/GetParam sum 0)
                                               (LLVM/GetParam sum 1)
                                               (genstr))
                                (genstr)))
  ;; main
  (LLVM/PositionBuilderAtEnd builder entry2)
  (LLVM/BuildRet builder
                 (LLVM/BuildCall builder
                                 sum
                                 (into-array [(LLVM/GetParam main 0)
                                              (LLVM/GetParam main 0)])
                                 (genstr)))
  
  
  (Symba/VerifyModule mod)  
  
  ;; writes llvm bitcode to disk
  (LLVM/WriteBitcodeToFile mod "repl-out.bc")
  
  (let [engine (make-engine mod)
        del (new-delegate-type "del" Int32 [Int32 Int32])
        jit-fn (Marshal/GetDelegateForFunctionPointer
                 (LLVM/GetPointerToGlobal engine sum)
                 del)
        ]
        
        (println (.Invoke jit-fn 20 100)) ;; => 140
    
    ;; writes native object file to disk
    (Symba/EmitModule
      engine
      mod
      LLVMCodeGenFileType/LLVMObjectFile
      "repl-out.o") 
    
    ;; writes native assembly to disk
    (Symba/EmitModule
      engine
      mod
      LLVMCodeGenFileType/LLVMAssemblyFile
      "repl-out.s")))