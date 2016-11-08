using System;
using System.Linq;
using System.Reflection;
using System.Reflection.Emit;
using System.Runtime.InteropServices;
using LLVMSharp;

namespace Symba
{
	public delegate int AddTest(float a, int b);

	public class Symba
	{
		public static Type NewConcreteGenericType(string name, Type returnType, Type[] argTypes)
		{
			var beginInvokeArgTypes = argTypes.Concat(new Type[] { typeof(AsyncCallback), typeof(object) }).ToArray();
			var ab = AssemblyBuilder.DefineDynamicAssembly(new AssemblyName("delegates"), AssemblyBuilderAccess.RunAndSave);
			var mb = ab.DefineDynamicModule("delegates", "delegates.dll");
			var tb = mb.DefineType(name,
								   TypeAttributes.Public |
								   TypeAttributes.AutoLayout |
								   TypeAttributes.AnsiClass |
								   TypeAttributes.Sealed,
								   typeof(MulticastDelegate));
			var method = tb.DefineMethod("Invoke", MethodAttributes.Public |
							MethodAttributes.HideBySig |
							MethodAttributes.NewSlot |
							MethodAttributes.Virtual,
							returnType, argTypes);
			method.GetILGenerator().Emit(OpCodes.Ret);
			method = tb.DefineMethod("BeginInvoke",
							MethodAttributes.Public |
							MethodAttributes.HideBySig |
							MethodAttributes.NewSlot |
							MethodAttributes.Virtual,
							typeof(IAsyncResult),
							beginInvokeArgTypes);
			method.GetILGenerator().Emit(OpCodes.Ret);
			method = tb.DefineMethod("EndInvoke",
							MethodAttributes.Public |
							MethodAttributes.HideBySig |
							MethodAttributes.NewSlot |
							MethodAttributes.Virtual,
							typeof(int),
							new Type[] { typeof(IAsyncResult) });
			method.GetILGenerator().Emit(OpCodes.Ret);
			var cb = tb.DefineConstructor(
				MethodAttributes.Public |
				MethodAttributes.HideBySig |
				MethodAttributes.SpecialName |
				MethodAttributes.RTSpecialName,
				CallingConventions.Standard,
				new Type[] { typeof(object), typeof(IntPtr) } );
			cb.GetILGenerator().Emit(OpCodes.Ret);

			return tb.CreateType();
		}
		public static string StringifyMessage(IntPtr p)
		{
			string result = Marshal.PtrToStringAnsi(p) ?? string.Empty;
			LLVM.DisposeMessage(p);
			return result;
		}

		public static LLVMTypeRef FunctionType(LLVMTypeRef returnType, LLVMTypeRef[] parameterTypes, uint parameterCount, LLVMBool isVarArg)
		{
			return LLVM.FunctionType(returnType, out parameterTypes[0], parameterCount, isVarArg);
		}

		public static void VerifyModule(LLVMModuleRef mod, LLVMVerifierFailureAction action)
		{
			IntPtr error;
			LLVM.VerifyModule(mod, action, out error);
			Console.WriteLine(StringifyMessage(error));
		}

		public static void VerifyModule(LLVMModuleRef mod)
		{
			VerifyModule(mod, LLVMVerifierFailureAction.LLVMAbortProcessAction);
		}

		public static void EmitModule(LLVMExecutionEngineRef ee, LLVMModuleRef mod, LLVMCodeGenFileType fileType, string filename)
		{
			IntPtr error;
			IntPtr ptr = Marshal.StringToHGlobalAnsi(filename);
			LLVM.TargetMachineEmitToFile(LLVM.GetExecutionEngineTargetMachine(ee), mod, ptr, fileType, out error);
			Console.WriteLine(StringifyMessage(error));
		}

		public static void EmitModule(LLVMExecutionEngineRef ee, LLVMModuleRef mod, string filename)
		{
			EmitModule(ee, mod, LLVMCodeGenFileType.LLVMObjectFile, filename);
		}

		public static LLVMExecutionEngineRef CompilerForModule(LLVMModuleRef mod)
		{
			IntPtr error;
			LLVMExecutionEngineRef engine;

			LLVM.LinkInMCJIT();
			LLVM.InitializeX86AsmPrinter();
			LLVM.InitializeX86Target();
			LLVM.InitializeX86TargetMC();
			LLVM.InitializeX86TargetInfo();
			LLVM.InitializeX86Disassembler();

			var options = new LLVMMCJITCompilerOptions();
			options.OptLevel = 3;
			options.CodeModel = LLVMCodeModel.LLVMCodeModelJITDefault;
			var optionsSize = (4 * sizeof(int)) + IntPtr.Size; // LLVMMCJITCompilerOptions has 4 ints and a pointer

			LLVM.InitializeMCJITCompilerOptions(out options, (ulong)optionsSize);
			LLVM.CreateMCJITCompilerForModule(out engine, mod, out options, (ulong)optionsSize, out error);
			Console.WriteLine(StringifyMessage(error));
			return engine;
		}
	}
}