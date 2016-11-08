symba - The Symbolic Assembler
==============================
A ClojureCLR library to metaprogram LLVM bitcode

```clojure
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
  ;; sum
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
;; ...
```

```llvm
; ModuleID = 'repl-out.bc'
target datalayout = "e-m:o-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-apple-macosx10.11.0"

define i32 @sum(i32, i32) {
entry:
  %"79" = add i32 %0, %1
  %"80" = add i32 %0, %"79"
  ret i32 %"80"
}

define i32 @main(i32, i32) {
entry:
  %"81" = call i32 @sum(i32 %0, i32 %0)
  ret i32 %"81"
}
```

```
.section	__TEXT,__text,regular,pure_instructions
.macosx_version_min 10, 11
.globl	_sum
.align	4, 0x90
_sum:
.cfi_startproc
movl	%edi, %eax
addl	%esi, %eax
addl	%eax, %edi
movl	%edi, %eax
retq
.cfi_endproc

.globl	_main
.align	4, 0x90
_main:
.cfi_startproc
pushq	%rax
Ltmp0:
.cfi_def_cfa_offset 16
movabsq	$_sum, %rax
movl	%edi, 4(%rsp)
movl	4(%rsp), %ecx
movl	%esi, (%rsp)
movl	%ecx, %esi
callq	*%rax
popq	%rcx
retq
.cfi_endproc


.subsections_via_symbols
```

Goal
----
This is an experiment. Like [MAGE](https://github.com/nasser/mage) does to MSIL, the goal here is to represent LLVM bitcode as a functional data structure to make functional compilers easier to write. This approach makes reasoning about low-level code easier while allowing you to experiment in the REPL as you would in standard Clojure programming. Despite how high level this all is, all of LLVM remains available and is not abstracted in any way.

Another way to think of it is the [Terra](http://terralang.org/) approach to low-level metaprogramming, but with Clojure instead of Lua.

Status
------
Very early and not terribly usable yet. Watch this space.

It can generate functions in memory, execute them, and write them to disk.

Usage
-----
Make sure LLVM is installed and its dynamic libraries are on the standard path. If you have a 64 bit version of LLVM you must use a 64 version of mono, which may not be the default.

Tested on OSX 10.11.6 with 64 bit LLVM 3.8.1, mono 4.6.0 and ClojureCLR 1.7.0.   There is a good chance this won't work for you just yet.

License
-------
[MIT](https://opensource.org/licenses/MIT)
