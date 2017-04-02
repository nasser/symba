symba - The Symbolic Assembler
==============================
A ClojureCLR library to metaprogram LLVM bitcode

```
$ nos symba/demo demo.bc
Compiling to demo.bc:
(symba/module
 [(symba/function
   "one-or-the-other"
   symba.types/i32
   [symba.types/i32 symba.types/i32]
   [(symba/block
     [(symba/br
       (symba/icmp :eq (symba/param 1) (symba/param 0))
       (symba/block
        [(symba/ret (symba/add (symba/param 0) (symba/param 1)))])
       (symba/block [(symba/ret (symba/param 1))]))])])])
$ opt -S demo.bc
; ModuleID = 'demo.bc'

define i32 @one-or-the-other(i32, i32) {
  %3 = icmp eq i32 %1, %0
  br i1 %3, label %4, label %6

; <label>:4:                                      ; preds = %2
  %5 = add i32 %0, %1
  ret i32 %5

; <label>:6:                                      ; preds = %2
  ret i32 %1
}
```

Status
------
Very early and not terribly usable yet. Expect everything to change. Watch this space.

It beginnings of a functional representation of LLVM bitcode are in place. symba can generate functions in memory, execute them, and write them to disk.

Usage
-----
symba bundles its LLVM dependencies for OSX but no other OS at the moment. symba is built and run using [nostrand](http://github.com/nasser/nostrand). Set nostrand up and run the Clojure/West 2017 demo with `nos symba/demo demo.bc` as above. Install LLVM for access to the `opt` command. LLVM is available via homebrew on OSX.

Goal
----
This is an experiment. Like [MAGE](https://github.com/nasser/mage) does to MSIL, the goal here is to represent LLVM bitcode as a functional data structure to make functional compilers easier to write. This approach makes reasoning about low-level code easier while allowing you to experiment in the REPL as you would in standard Clojure programming. Despite how high level this all is, all of LLVM remains available and is not abstracted in any way.

Another way to think of it is the [Terra](http://terralang.org/) approach to low-level metaprogramming, but with Clojure instead of Lua.

License
-------
[MIT](https://opensource.org/licenses/MIT)
