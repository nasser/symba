(ns symba.types
  (:refer-clojure :exclude [float double])
  (:import [LLVMSharp LLVM LLVMBool]
           [Symba Symba]))

(def i1 (LLVM/Int1Type))
(def i8 (LLVM/Int8Type))
(def i16 (LLVM/Int16Type))
(def i32 (LLVM/Int32Type))
(def i64 (LLVM/Int64Type))
(def i128 (LLVM/Int128Type))

(def half (LLVM/HalfType))
(def float (LLVM/FloatType))
(def double (LLVM/DoubleType))

(def x86fp80 (LLVM/X86FP80Type))
(def fp128 (LLVM/FP128Type))
(def ppcfp128 (LLVM/PPCFP128Type))

(def void (LLVM/VoidType))

(defn function
  ([return parameters]
   (function return parameters false))
  ([return parameters varargs?]
   (Symba/FunctionType
     return (into-array parameters) (count parameters)
     (if varargs? (LLVMBool. 1) (LLVMBool. 0)))))
