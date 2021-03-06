package pdp11;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile; 
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.Stack;

/*
 * Kernel Class
 */
public class Kernel{
	
	/*変数定義------------------------------------------*/
	
	Process[] proc; //プロセス
	int nowProcessNo; //現在処理中のプロセスNo
	
	Register reg; //レジスタ
	FileDescriptor fd; //ファイルディスクリプタ
	ConditionCode cc; //コンディションコード
	Signal signal; //シグナル
	
	VirtualAddressSpace pva; //親プロセス
	
	int strnum; //出力用ユーティリティ
	Stack<Byte> argStack; //スタック生成用
	
	int flgDebugMode; //デバッグモード
	boolean flgDismMode; //逆アセンブルモード
	boolean flgExeMode; //実行モード
	boolean flgMemoryDump; //デバッグモード（メモリダンプ）
	int flgOsMode; //OSモード
	

	/*コンストラクタ------------------------------------------*/

	Kernel(int maxProcess,int inFlgDebugMode,boolean inFlgDismMode, boolean inFlgExeMode,boolean inFlgMemoryDump,int inFlgOsMode){
		proc = new Process[maxProcess];
		
		nowProcessNo = 0; //プロセスNoは0からスタート

		flgDebugMode = inFlgDebugMode;
		flgDismMode = inFlgDismMode;
		flgExeMode = inFlgExeMode;
		flgMemoryDump = inFlgMemoryDump;
		flgOsMode = inFlgOsMode;

		reset();
		}

	
	/*ユーティリティ関数------------------------------------------*/

	//レジスタ関連生成
	void reset(){
		reg = new Register(); //レジスタ生成
		fd = new FileDescriptor(); //ファイルディスクリプタ生成
		cc = new ConditionCode(); //コンディションコード生成
		signal = new Signal(); //シグナル生成
	}
	
	//カーネルスタート
	void start(String[] args,int argsNo){
		createProcess(args,argsNo,false); //プロセス生成
		if(flgDismMode) dissAssemble(); //逆アセンブル
		setExecute(flgDebugMode, flgMemoryDump, argStack); //実行前設定
		if(flgExeMode) execute(); //実行
	}
	
	//プロセス生成
	void createProcess(String[] args,int argsNo,boolean inFlgChildProcess){
		proc[nowProcessNo] = new Process(nowProcessNo,inFlgChildProcess); //プロセスインスタンス作成
		setStack(args, argsNo); //スタック設定
		readBinary(args[argsNo]); //プロセスにバイナリを読み込み
	}
	
	//逆アセンブラ呼び出し
	void dissAssemble(){
		disassemble(0,proc[nowProcessNo].vas.textSize);
	}
	
	//インタプリタ実行前設定
	public void setExecute(int debugFlg,boolean memoryFlg, Stack<Byte> args){
		flgDebugMode = debugFlg; //デバッグフラグ
		flgMemoryDump = memoryFlg; //デバッグフラグ
		
		reg.reset(); //レジスタ初期化
		cc.reset(); //コンディションコード初期化
		signal.reset(); //シグナル初期化

		//引数をスタックに設定
		ArrayList<Integer> valAddress = new ArrayList<Integer> ();
		boolean flgStack = false;
		int valNum = 0;
		while(true){
			byte a;
			byte b;
			try{
				a = args.pop();
				if(a != 0 && !flgStack) flgStack=true;
				if(a == 0 && flgStack) valAddress.add(valNum);
				valNum++;
				b = args.pop();
				if(b != 0 && !flgStack) flgStack=true;
				if(b == 0 && flgStack) valAddress.add(valNum);
				valNum++;
			}catch(EmptyStackException e){
				break;
			}
			pushStack((int) ((int)(b & 0xFF)|( (int)(a & 0xFF) << 8)));
		}

		if(flgStack) valAddress.add(valNum);

		for(int j=0;j<valAddress.size();j++){
			pushStack(65536 - valAddress.get(j));
		}
		pushStack(valAddress.size());
	}
	
	//実行呼び出し
	void execute(){
		execute(0, proc[nowProcessNo].vas.textSize);
	}
	
	//スタックの設定（String[]）
	void setStack(String[] args,int argsNo){
		
		//引数をbyte配列に変換
		ArrayList<byte[]> arg = new ArrayList<byte[]>();
		int argSize = 0;
		int argCnt = 0;
		for(;argsNo<args.length;argsNo++){
			arg.add(args[argsNo].getBytes());
			argSize = argSize + arg.get(argCnt).length + 1;
			argCnt++;
		}
		
		//引数をByteのStackに積み込み
		argStack = new Stack<Byte>();
		for(int j=0;j<arg.size();j++){
			for(int k=0;k<arg.get(j).length;k++){
				argStack.push(arg.get(j)[k]);
			}
			argStack.push((byte)0);
		}
		if(argSize%2!=0){
			argStack.push((byte)0);
		}
	}
	
	//スタックの設定（ArrayList<String>）
	void setStack(ArrayList<String> execArgs){
		int argSize = 0;
		argStack = new Stack<Byte>();

		for(int j=0;j<execArgs.size();j++){
			argSize = argSize + execArgs.get(j).length() + 1;
			for(int k=0;k<execArgs.get(j).length();k++){
				argStack.push((byte)execArgs.get(j).charAt(k));
			}
			argStack.push((byte)0);
		}
		if(argSize%2!=0){
			argStack.push((byte)0);
		}
	}
	
	//バイナリ読み込み
	void readBinary(String strFileName){
		File file = new File(strFileName);
		Path fileName = file.toPath();

		//ファイル内容取得
		byte[] bf = null;
		try {
	        bf = java.nio.file.Files.readAllBytes(fileName);
		} catch (IOException e) {
			e.printStackTrace();
		}

		//仮想メモリにロード
		proc[nowProcessNo].vas = new VirtualAddressSpace(bf);
	}
	
	//ASCIIコードに変換したデータを表示
	void printChar(int dec){
		System.out.print((char)Integer.parseInt(String.format("%02x",dec),16));
	}

	//指定した命令を出力
	void printOpcode(int opcode){
		System.out.print(String.format("%04x", opcode));
		System.out.print(" ");
	}
	
	//2バイト単位でリトルエンディアンを反転して10進数で取得
	int getMemory2(int start){
		return proc[nowProcessNo].vas.getMemory2(start);
	}

	//1バイト単位で指定箇所のメモリを取得
	int getMemory1(int start){
		return proc[nowProcessNo].vas.getMemory1(start);
	}

	//2バイト単位で指定箇所のメモリを更新
	void setMemory2(int add,int src){
		proc[nowProcessNo].vas.setMemory2(add,src);
	}

	//1バイト単位で指定箇所のメモリを更新
	void setMemory1(int add,int src){
		proc[nowProcessNo].vas.setMemory1(add,src);
	}
	
	//メモリ上のデータを取得して、PC+2する
	int getMem(){
		int opcode = getMemory2(reg.get(7));

		//逆アセンブルの場合は出力
		if(flgExeMode){
			if(flgDebugMode>1) printOpcode(opcode);
		}else{
			printOpcode(opcode);
			strnum++;
		}
		
		reg.add(7,2); //PC+2

		return opcode;
	}

	//8進数から10進数に変換
	int getDex(int first,int second){
		return Integer.parseInt(Integer.toString(first * 10 + second), 8);
	}
		
	//レジスタ名称取得
	String getRegisterName(int no){
		if(no == 7){
			return "pc";
		}else if(no == 6){
			return "sp";
		}else{
			return "r" + no;
		}
	}

	//スタック積む
	void pushStack(int n){
		reg.add(6,-2);
		setMemory2(reg.get(6),n);
	}

	
	/*逆アセンブル関数------------------------------------------*/

	void disassemble(int start, int end){
		flgExeMode = false; //実行モードオフ

		reg.reset(); //レジスタ初期化

		FieldDto dstObj = new FieldDto();
		FieldDto srcObj = new FieldDto();
		
		//逆アセンブル
		for(reg.set(7, start);reg.get(7)<end;){

			//プログラムカウンタを出力
			System.out.print(String.format("%4x", reg.get(7)));
			System.out.print(":   ");

			strnum = 0;

			String mnemonic = "";
			String srcOperand = "";
			String dstOperand = "";

			int opcode = getMem();
			Mnemonic nic = getMnemonic(opcode);

			switch(nic){
			case ADC:
				mnemonic = "adc";
				srcOperand = "";
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case ADD:
				mnemonic = "add";
				srcOperand = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7).str;
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case ASH:
				mnemonic = "ash";
				srcOperand = getField(srcObj,(opcode >> 3) & 7,opcode  & 7).str;
				dstOperand = getRegisterName((opcode >> 6) & 7);
				break;
			case ASHC:
				mnemonic = "ashc";
				srcOperand = getField(srcObj,(opcode >> 3) & 7,opcode  & 7).str;
				dstOperand = getRegisterName((opcode >> 6) & 7);
				break;
			case ASL:
				mnemonic = "asl";
				srcOperand = "";
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case ASR:
				mnemonic = "asr";
				srcOperand = "";
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BCC:
				mnemonic = "bcc";
				srcOperand = "";
				dstOperand = getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BCS:
				mnemonic = "bcs";
				srcOperand = "";
				dstOperand = getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BEQ:
				mnemonic = "beq";
				srcOperand = getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).str;
				dstOperand = "";
				break;
			case BGE:
				mnemonic = "bge";
				srcOperand = "";
				dstOperand = getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BGT:
				mnemonic = "bgt";
				srcOperand = "";
				dstOperand = getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BHI:
				mnemonic = "bhi";
				srcOperand = "";
				dstOperand = getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BIC:
				mnemonic = "bic";
				srcOperand = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7).str;
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BICB:
				mnemonic = "bicb";
				srcOperand = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7).str;
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BIS:
				mnemonic = "bis";
				srcOperand = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7).str;
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BISB:
				mnemonic = "bisb";
				srcOperand = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7).str;
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BIT:
				mnemonic = "bit";
				srcOperand = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7).str;
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BITB:
				mnemonic = "bitb";
				srcOperand = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7).str;
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BLE:
				mnemonic = "ble";
				srcOperand = "";
				dstOperand = getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BLOS:
				mnemonic = "blos";
				srcOperand = "";
				dstOperand = getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BLT:
				mnemonic = "blt";
				srcOperand = "";
				dstOperand = getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BMI:
				mnemonic = "bmi";
				srcOperand = "";
				dstOperand = getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BNE:
				mnemonic = "bne";
				srcOperand = "";
				dstOperand = getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BPL:
				mnemonic = "bpl";
				srcOperand = "";
				dstOperand = getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BR:
				mnemonic = "br";
				srcOperand = "";
				dstOperand = getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case BVS:
				mnemonic = "bvs";
				srcOperand = "";
				dstOperand = getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case CLR:
				mnemonic = "clr";
				srcOperand = "";
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case CLRB:
				mnemonic = "clrb";
				srcOperand = "";
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case CMP:
				mnemonic = "cmp";
				srcOperand = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7).str;
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case CMPB:
				mnemonic = "cmpb";
				srcOperand = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7).str;
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case COM:
				mnemonic = "com";
				srcOperand = "";
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case DEC:
				mnemonic = "dec";
				srcOperand = "";
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case DECB:
				mnemonic = "decb";
				srcOperand = "";
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case DIV:
				mnemonic = "div";
				srcOperand = getField(srcObj,(opcode >> 3) & 7,opcode  & 7).str;
				dstOperand = getRegisterName((opcode >> 6) & 7);
				break;
			case INC:
				mnemonic = "inc";
				srcOperand = "";
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case INCB:
				mnemonic = "incb";
				srcOperand = "";
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case JMP:
				mnemonic = "jmp";
				srcOperand = "";
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case JSR:
				mnemonic = "jsr";
				srcOperand = getRegisterName((opcode >> 6) & 7);
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case MOV:
				mnemonic = "mov";
				srcOperand = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7).str;
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case MOVB:
				mnemonic = "movb";
				srcOperand = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7).str;
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case MUL:
				mnemonic = "mul";
				srcOperand = getField(srcObj,(opcode >> 3) & 7,opcode  & 7).str;
				dstOperand = getRegisterName((opcode >> 6) & 7);
				break;
			case NEG:
				mnemonic = "neg";
				srcOperand = "";
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case ROL:
				mnemonic = "rol";
				srcOperand = "";
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case ROR:
				mnemonic = "ror";
				srcOperand = "";
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case RTS:
				mnemonic = "rts";
				srcOperand = "";
				dstOperand = getRegisterName(opcode  & 7);
				break;
			case RTT:
				mnemonic = "rtt";
				srcOperand = "";
				dstOperand = "";
				break;
			case SETD:
				mnemonic = "setd";
				srcOperand = "";
				dstOperand = "";
				break;
			case SEV:
				mnemonic = "sev";
				srcOperand = "";
				dstOperand = "";
				break;
			case SOB:
				mnemonic = "sob";
				srcOperand = getRegisterName((opcode >> 6) & 7);
				dstOperand = getOffset6(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case SUB:
				mnemonic = "sub";
				srcOperand = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7).str;
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case SWAB:
				mnemonic = "swab";
				srcOperand = "";
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case SXT:
				mnemonic = "sxt";
				srcOperand = "";
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case SYS:
				if(getDex((opcode >> 3) & 7,opcode  & 7) == 0) getMem();
				mnemonic = "sys";
				srcOperand = "";
				dstOperand = String.valueOf(opcode  & 7);
				break;
			case TST:
				mnemonic = "tst";
				srcOperand = "";
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case TSTB:
				mnemonic = "tstb";
				srcOperand = "";
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case XOR:
				mnemonic = "xor";
				srcOperand = String.valueOf(reg.get((opcode >> 6) & 7));
				dstOperand = getField(dstObj,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			case WORD:
				mnemonic = ".word";
				srcOperand = "";
				dstOperand = getNormal(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).str;
				break;
			}

			//出力
			for(;strnum<3;strnum++) System.out.print("     ");

			System.out.print(" ");
			System.out.print(mnemonic);

			for(int j=mnemonic.length();j<9;j++) System.out.print(" ");

			System.out.print(srcOperand);

			if(!(srcOperand.equals("")) && !(dstOperand.equals(""))) System.out.print(", ");

			System.out.print(dstOperand + "\n");
		}
	}


	/*インタプリタ関数------------------------------------------*/

	//実行呼び出し
	public void execute(int start, int end){
		execute(start, end, false,false);
	}

	//実行呼び出し
	public void execute(int start, int end,boolean endFlg){
		execute(start, end, endFlg,false);
	}

	//インタプリタ
	public void execute(int start, int end, boolean endFlg,boolean forkFlg){
		flgExeMode = true; //実行モードオン
		
		if(!forkFlg) reg.set(7,start); //PCを初期化
		if(!endFlg) end = 65536;

		FieldDto srcObj = new FieldDto();
		FieldDto dstObj = new FieldDto();

		for(;reg.get(7)<end;){
			if(flgDebugMode>1) printDebug(); //レジスタ・フラグ出力
			if(flgMemoryDump) printMemory(); //メモリダンプ出力

			//ワーク
			int tmp = 0;
			
			int opcode = getMem(); //命令取得
			Mnemonic nic = getMnemonic(opcode); //ニーモニック取得
			
			switch(nic){
			case ADC:
				getField(dstObj,(opcode >> 3) & 7,opcode & 7);
				
				int adctmp = 0;
				if(cc.c) adctmp = 1;
				
				int adctmp2 = 0;
				if(dstObj.flgRegister){
					adctmp2 = reg.get(dstObj.register);
					tmp = reg.get(dstObj.register) + adctmp;
					reg.set(dstObj.register, tmp);
				}else if(dstObj.flgAddress){
					adctmp2 = getMemory2(dstObj.address);
					tmp = getMemory2(dstObj.address) + adctmp;
					setMemory2(dstObj.address, tmp);
				}else{
					adctmp2 = dstObj.operand;
					tmp = dstObj.operand + adctmp;
					setMemory2(dstObj.address, tmp);
				}

				cc.set((tmp << 16 >>> 31)>0, tmp==0, ((adctmp2 == 077777) && adctmp == 1), ((adctmp2 == -1) && adctmp == 1));
				
				break;
			case ADD:
				srcObj = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7);
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				tmp = srcObj.operand + dstObj.operand;
				
				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else{
					setMemory2(dstObj.address, tmp);
				}				
				
				cc.set((tmp >> 15)>0, 
						tmp==0, 
						getAddOverflow(srcObj.operand, dstObj.operand, tmp), 
						getAddCarry(srcObj.operand, dstObj.operand, tmp));

				break;
			case ASH: 
                int ashReg = reg.get((opcode >> 6) & 7);
                srcObj = getField(srcObj,(opcode >> 3) & 7,opcode  & 7);
                int ashInt = srcObj.operand << 26;
                ashInt = ashInt >> 26;
                if(ashInt < 0){
                        ashReg = ashReg << 16;
                        ashReg = ashReg >> 16;
                        reg.set((opcode >> 6) & 7, ashReg >> Math.abs(ashInt));
                        reg.set((opcode >> 6) & 7, (reg.get((opcode >> 6) & 7) << 16) >>> 16);
                }else{
                        reg.set((opcode >> 6) & 7, ashReg << ashInt);
                        reg.set((opcode >> 6) & 7, (reg.get((opcode >> 6) & 7) << 16) >>> 16);
                }
                
                cc.set((reg.get((opcode >> 6) & 7) << 1 >>> 16)>0, //TODO
                                reg.get((opcode >> 6) & 7)==0, 
                                ((ashReg << 16 ) >>> 31) != ((reg.get((opcode >> 6) & 7) << 16) >>> 31), //TODO
                                false); //TODO
                
				break;
			case ASHC: //TODO
				int ashcReg1 = reg.get((opcode >> 6) & 7);
				int ashcReg2 = reg.get(((opcode >> 6) & 7) + 1);
				int ashcTmp = (ashcReg1 << 16) + (ashcReg2 << 16 >>> 16);
				
				srcObj = getField(srcObj,(opcode >> 3) & 7,opcode  & 7);
				int ashcInt = srcObj.operand << 26 >> 26;
			
				if(ashcInt < 0){
					tmp = ashcTmp >> Math.abs(ashcInt);
					reg.set((opcode >> 6) & 7, ashcTmp >> Math.abs(ashcInt) >>> 16);
					reg.set(((opcode >> 6) & 7)+1, ashcTmp >> Math.abs(ashcInt) << 16 >>> 16);
				}else{
					tmp = ashcTmp << Math.abs(ashcInt);
					reg.set((opcode >> 6) & 7, ashcTmp << Math.abs(ashcInt) >>> 16);
					reg.set(((opcode >> 6) & 7)+1, ashcTmp << Math.abs(ashcInt) << 16 >>> 16);
				}
				
				cc.set(tmp>0, //TODO
						tmp==0, 
						(ashcTmp >>> 31) != (tmp  >>> 31), //TODO
						false); //TODO
				
				break;
			case ASL:
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				tmp = dstObj.operand << 1;
				
				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else{
					setMemory2(dstObj.address, tmp);
				}				

				cc.set((tmp >> 15)>0, tmp==0, cc.v, (tmp >>> 16)>0);
				cc.set(cc.n, cc.z, cc.n^cc.c, cc.c);

				break;
			case ASR:
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				tmp = dstObj.operand << 16 >> 16;
				tmp = tmp >> 1;
				
				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else{
					setMemory2(dstObj.address, tmp);
				}				

				cc.set((tmp >> 15)>0, tmp==0, cc.v, (tmp >>> 16)>0);
				cc.set(cc.n, cc.z, cc.n^cc.c, cc.c);

				break;
			case BCC:
				if(cc.c == false) reg.set(7,getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).address);
				break;
			case BCS:
				if(cc.c == true) reg.set(7,getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).address);
				break;
			case BEQ:
				if(cc.z == true) reg.set(7,getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).address);
				break;
			case BGE:
				if(cc.n == cc.v) reg.set(7,getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).address);
				break;
			case BGT:
				if(cc.z == false && cc.n == cc.v) reg.set(7,getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).address);
				break;
			case BHI:
				if(cc.c == false && cc.z == false) reg.set(7,getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).address);
				break;
			case BIC:
				srcObj = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7);
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);

				tmp = ~(srcObj.operand) & dstObj.operand;

				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else{
					setMemory2(dstObj.address, tmp);
				}
				
				cc.set((tmp << 16 >>> 31) > 0, tmp==0, false, cc.c);

				break;
			case BICB:
				srcObj = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7,true);
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7,true);

				tmp = ~(srcObj.operand) & dstObj.operand;

				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else{
					setMemory1(dstObj.address, tmp);
				}

				cc.set(tmp>0xFF, tmp==0, false, cc.c);

				break;
			case BIS:
				srcObj = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7);
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);

				if(srcObj.flgRegister){
					if(dstObj.flgRegister){
						tmp = reg.get(srcObj.register) | reg.get(dstObj.register);
						reg.set(dstObj.register, reg.get(srcObj.register) | reg.get(dstObj.register));
					}else if(dstObj.flgAddress){
						tmp = reg.get(srcObj.register) | getMemory2(dstObj.address);
						setMemory2(dstObj.address, reg.get(srcObj.register) | getMemory2(dstObj.address));
					}

				}else if(srcObj.flgAddress){
					if(dstObj.flgRegister){
						tmp = getMemory2(srcObj.address) | reg.get(dstObj.register);
						reg.set(dstObj.register, getMemory2(srcObj.address) | reg.get(dstObj.register));
					}else if(dstObj.flgAddress){
						tmp = getMemory2(srcObj.address) | getMemory2(dstObj.address);
						setMemory2(dstObj.address, getMemory2(srcObj.address) | getMemory2(dstObj.address));
					}
				}else{
					if(dstObj.flgRegister){
						tmp = srcObj.operand | reg.get(dstObj.register);
						reg.set(dstObj.register, srcObj.operand | reg.get(dstObj.register));
					}else if(dstObj.flgAddress){
						tmp = srcObj.operand | getMemory2(dstObj.address);
						setMemory2(dstObj.address, srcObj.operand | getMemory2(dstObj.address));
					}
				}
				
				cc.set(false, //TODO 
						tmp==0, 
						false, 
						cc.c);

				break;
			case BISB:
				srcObj = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7);
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				
				if(srcObj.flgRegister){
					if(dstObj.flgRegister){
						tmp = (reg.get(srcObj.register) | reg.get(dstObj.register)) << 24 >>> 24;
						reg.set(dstObj.register, (reg.get(srcObj.register) | reg.get(dstObj.register)) << 24 >>> 24);
					}else if(dstObj.flgAddress){
						tmp = (reg.get(srcObj.register) | getMemory2(dstObj.address)) << 24 >>> 24;
						setMemory1(dstObj.address, (reg.get(srcObj.register) | getMemory2(dstObj.address)) << 24 >>> 24);
					}

				}else if(srcObj.flgAddress){
					if(dstObj.flgRegister){
						tmp = (getMemory2(srcObj.address) | reg.get(dstObj.register)) << 24 >>> 24;
						reg.set(dstObj.register, (getMemory2(srcObj.address) | reg.get(dstObj.register)) << 24 >>> 24);
					}else if(dstObj.flgAddress){
						tmp = (getMemory2(srcObj.address) | getMemory2(dstObj.address)) << 24 >>> 24;
						setMemory1(dstObj.address, (getMemory2(srcObj.address) | getMemory2(dstObj.address)) << 24 >>> 24);
					}
				}else{
					if(dstObj.flgRegister){
						tmp = (srcObj.operand | reg.get(dstObj.register)) << 24 >>> 24;
						reg.set(dstObj.register, (srcObj.operand | reg.get(dstObj.register)) << 24 >>> 24);
					}else if(dstObj.flgAddress){
						tmp = (srcObj.operand | getMemory2(dstObj.address)) << 24 >>> 24;
						setMemory1(dstObj.address, (srcObj.operand | getMemory2(dstObj.address)) << 24 >>> 24);
					}
				}
				
				cc.set(false, //TODO
						tmp == 0, 
						false, 
						cc.c);

				break;
			case BIT:
				srcObj = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7);
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				tmp = srcObj.operand & dstObj.operand;
				
				cc.set(false, //TODO 
						tmp==0, 
						false, 
						cc.c);

				break;
			case BITB:
				srcObj = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7);
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				tmp = srcObj.operand & dstObj.operand;
				tmp = tmp << 24 >>> 24;
				
				cc.set(false, //TODO 
						tmp==0, 
						false, 
						cc.c);

				break;
			case BLE:
				if(cc.z == true || cc.n != cc.v) reg.set(7,getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).address);
				break;
			case BLOS:
				if(cc.c == true || cc.z == true) reg.set(7,getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).address);
				break;
			case BLT:
				if(cc.n != cc.v) reg.set(7,getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).address);
				break;
			case BMI:
				if(cc.n == true) reg.set(7,getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).address);
				break;
			case BNE:
				if(cc.z == false) reg.set(7,getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).address);
				break;
			case BPL:
				if(cc.n == false) reg.set(7,getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).address);
				break;
			case BR:
				reg.set(7,getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).address);
				break;
			case BVS:
				if(cc.v == true) reg.set(7,getOffset(dstObj,(opcode >> 6) & 7,(opcode >> 3) & 7,opcode  & 7).address);
				break;
			case CLR:
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				if(dstObj.flgRegister){
					reg.set(dstObj.register, 0);
				}else{
					setMemory2(dstObj.address,0);
				}
				
				cc.set(false, true, false, false);
				
				break;
			case CLRB:
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7, true);
				if(dstObj.flgRegister){
					reg.set(dstObj.register, 0);
				}else{
					setMemory1(dstObj.address,0);
				}
				
				cc.set(false, true, false, false);
				
				break;
			case CMP:
				srcObj = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7);
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				tmp = (srcObj.operand << 16 >>> 16) - (dstObj.operand << 16 >>> 16);
				
				cc.set((tmp << 16 >>> 31)>0, 
						tmp==0, 
						getSubOverflow(srcObj.operand, dstObj.operand, tmp), 
						getSubBorrow(srcObj.operand, dstObj.operand, tmp));

				break;
			case CMPB:
				srcObj = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7, true);
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7, true);

				tmp = (srcObj.operand << 24 >>> 24) - (dstObj.operand << 24 >>> 24);
				
				cc.set((tmp << 1 >>> 16)>0, 
						tmp==0, 
						getSubOverflow(srcObj.operand << 24 >>> 24, dstObj.operand << 24 >>> 24, tmp), 
						getSubBorrow(srcObj.operand << 24 >>> 24, dstObj.operand << 24 >>> 24, tmp));

				break;
			case COM:
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				if(dstObj.flgRegister){
					reg.set(dstObj.register, ~dstObj.operand);
				}else{
					setMemory2(dstObj.address, ~dstObj.operand);
				}
				
				cc.set(((~dstObj.operand)<<16>>>31)>0, ((~dstObj.operand)<<16>>>16)==0, false, true);
				
				break;
			case DEC:
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				
				if(dstObj.flgRegister){
					tmp = reg.get(dstObj.register) - 1;
					reg.set(dstObj.register, tmp);
				}else if(dstObj.flgAddress){
					tmp = getMemory2(dstObj.address) - 1;
					setMemory2(dstObj.address, tmp);
				}else{
					tmp = dstObj.operand - 1;
					setMemory2(dstObj.address, tmp);
				}

				cc.set((tmp << 16 >>> 31)>0, tmp==0, cc.v, cc.c);
				
				break;
			case DECB:
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7, true);
				if(dstObj.flgRegister){
					tmp = reg.get(dstObj.register) - 1;
					reg.set(dstObj.register, tmp);
				}else if(dstObj.flgAddress){
					tmp = getMemory2(dstObj.address) - 1;
					setMemory2(dstObj.address, tmp);
				}else{
					tmp = dstObj.operand - 1;
					setMemory2(dstObj.address, tmp);
				}

				cc.set((tmp << 1 >>> 16)>0, tmp==0, cc.v, cc.c);
				
				break;
			case DIV: 
				int divR1 = reg.get((opcode >> 6) & 7) << 16;
				int divR2 = reg.get(((opcode >> 6) & 7)+1);
				
				int divValue = divR1 + divR2;
				
				srcObj = getField(srcObj,(opcode >> 3) & 7,opcode & 7);
				
				reg.set((opcode >> 6) & 7, divValue / srcObj.operand);
				reg.set(((opcode >> 6) & 7)+1, divValue % srcObj.operand);

				cc.set((reg.get((opcode >> 6) & 7) >> 15)>0, 
						reg.get((opcode >> 6) & 7)==0, 
						srcObj.operand==0, //TODO
						srcObj.operand==0);
				
				break;
			case INC:
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				if(dstObj.flgRegister){
					tmp = reg.get(dstObj.register) + 1;
					reg.set(dstObj.register, tmp);
				}else if(dstObj.flgAddress){
					tmp = getMemory2(dstObj.address) + 1;
					setMemory2(dstObj.address, tmp);
				}else{
					tmp = dstObj.operand + 1;
					setMemory2(dstObj.address, tmp);
				}

				cc.set((tmp << 1 >>> 16)>0, tmp==0, cc.v, cc.c);

				break;
			case INCB:
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7,true);
				if(dstObj.flgRegister){
					tmp = reg.get(dstObj.register) + 1;
					reg.set(dstObj.register, tmp);
				}else if(dstObj.flgAddress){
					tmp = getMemory2(dstObj.address) + 1;
					setMemory1(dstObj.address, tmp);
				}else{
					tmp = dstObj.operand + 1;
					setMemory1(dstObj.address, tmp);
				}

				cc.set((tmp << 1 >>> 16)>0, tmp==0, cc.v, cc.c);

				break;
			case JMP:
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);

				if(dstObj.flgRegister){
					reg.set(7,reg.get(dstObj.register));
				}else if(dstObj.flgAddress){
					reg.set(7,dstObj.address);
				}else{
					reg.set(7,dstObj.operand);
				}
				
				cc.set(cc.n, cc.z, cc.v, cc.c);

				break;
			case JSR:
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				pushStack(reg.get((opcode >> 6) & 7));
				reg.set((opcode >> 6) & 7,reg.get(7));
				reg.set(7, dstObj.address);

				cc.set(cc.n, cc.z, cc.v, cc.c);

				break;
			case MOV:
				srcObj = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7);
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);

				if(srcObj.flgRegister){
					if(dstObj.flgRegister){
						reg.set(dstObj.register, reg.get(srcObj.register));
					}else if(dstObj.flgAddress){
						setMemory2(dstObj.address, reg.get(srcObj.register));
					}
				}else if(srcObj.flgAddress){
					if(dstObj.flgRegister){
						reg.set(dstObj.register, getMemory2(srcObj.address));
					}else if(dstObj.flgAddress){
						setMemory2(dstObj.address, getMemory2(srcObj.address));
					}
				}else{
					if(dstObj.flgRegister){
						reg.set(dstObj.register, srcObj.operand);
					}else if(dstObj.flgAddress){
						setMemory2(dstObj.address, srcObj.operand);
					}
				}

				cc.set((srcObj.operand << 1 >>> 16)>0, srcObj.operand==0, false, cc.c);

				break;
			case MOVB:
				srcObj = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7, true);
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7, true);
				
				if(srcObj.flgRegister){
					if(dstObj.flgRegister){
						//モード0の場合、符号拡張を行う
						tmp = reg.get(srcObj.register) << 24;
						tmp = tmp >> 24;
						reg.set(dstObj.register, tmp);
					}else if(dstObj.flgAddress){
						tmp = reg.get(srcObj.register);
						setMemory1(dstObj.address, tmp);
					}
				}else if(srcObj.flgAddress){
					if(dstObj.flgRegister){
						//モード0の場合、符号拡張を行う
						tmp = getMemory1(srcObj.address) << 24;
						tmp = tmp >> 24;
						reg.set(dstObj.register, tmp);
					}else if(dstObj.flgAddress){
						tmp = getMemory1(srcObj.address);
						setMemory1(dstObj.address, tmp);
					}
				}else{
					if(dstObj.flgRegister){
						//モード0の場合、符号拡張を行う
						tmp = srcObj.operand << 24;
						tmp = tmp >> 24;
						reg.set(dstObj.register, tmp);
					}else if(dstObj.flgAddress){
						tmp = srcObj.operand;
						setMemory1(dstObj.address, tmp);
					}
				}

				cc.set((tmp << 1 >>> 16)>0, tmp==0, false, cc.c);
				
				break;
			case MUL: //TODO
				int mulR = reg.get((opcode >> 6) & 7);
				srcObj = getField(srcObj,(opcode >> 3) & 7,opcode  & 7);
				
				if(((opcode >> 6) & 7) %2 ==0){
					reg.set((opcode >> 6) & 7, (mulR * srcObj.operand >> 16) << 16);
					reg.set(((opcode >> 6) & 7)+1, (mulR * srcObj.operand << 16) >>> 16);
				}else{
					reg.set((opcode >> 6) & 7, (mulR * srcObj.operand << 16) >>> 16);
				}
				cc.set((mulR * srcObj.operand  >>> 15)>0, 
						mulR * srcObj.operand==0, 
						false,
						false);
				break;
			case NEG:
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				tmp = (~(dstObj.operand << 16) >>> 16) + 1;
				
				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else{
					setMemory2(dstObj.address, tmp);
				}				

				cc.set((tmp >> 15)>0, tmp==0, tmp==100000, tmp!=0);

				break;
			case RTS:
				reg.set(7,reg.get(opcode  & 7));
				reg.set(opcode  & 7,getMemory2(reg.get(6)));
				reg.add(6,2);
				
				cc.set(cc.n, cc.z, cc.v, cc.c);

				break;
			case ROL:
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				int roltmp = 0;
				if(cc.c) roltmp = 1;
				if(dstObj.operand << 16 >>> 31 == 1) cc.c = true;
				if(dstObj.operand << 16 >>> 31 == 0) cc.c = false;
				
				if(dstObj.flgRegister){
					tmp = (reg.get(dstObj.register) << 1) + roltmp;
					reg.set(dstObj.register, tmp);
				}else if(dstObj.flgAddress){
					tmp =  (getMemory2(dstObj.address) << 1) + roltmp;
					setMemory2(dstObj.address, tmp);
				}else{
					tmp = (dstObj.operand >> 1) + roltmp;
					setMemory2(dstObj.address, tmp);
				}

				cc.set(cc.n, cc.z, cc.v, cc.c);

				break;
			case ROR:
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				int rortmp = 0;
				if(cc.c) rortmp = 1;
				if(dstObj.operand << 31 >>> 31 == 1) cc.c = true;
				if(dstObj.operand << 31 >>> 31 == 0) cc.c = false;
				
				if(dstObj.flgRegister){
					tmp = (rortmp << 15) + (reg.get(dstObj.register) << 16 >>> 16 >> 1);
					reg.set(dstObj.register, tmp);
				}else if(dstObj.flgAddress){
					tmp =  (rortmp << 15) + (getMemory2(dstObj.address) << 16 >>> 16 >> 1);
					setMemory2(dstObj.address, tmp);
				}else{
					tmp =  (rortmp << 15) + (dstObj.operand << 16 >>> 16 >> 1);
					setMemory2(dstObj.address, tmp);
				}

				cc.set(cc.n, cc.z, cc.v, cc.c);

				break;
			case RTT:
			case SETD:
				break;
			case SEV:
				cc.set(cc.n, cc.z, true, cc.c);
				break;
			case SOB:
				short tmpShort = (short)(reg.get((opcode >> 6) & 7) - 1);
				reg.set((opcode >> 6) & 7,((reg.get((opcode >> 6) & 7) - 1) << 16) >>> 16);
				if(tmpShort != 0) reg.set(7,getOffset6(dstObj,(opcode >> 3) & 7,opcode  & 7).address);
				break;
			case SUB:
				srcObj = getField(srcObj,(opcode >> 9) & 7,(opcode >> 6) & 7);
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);

				tmp = (dstObj.operand - srcObj.operand);
				tmp = tmp << 16 >>> 16;

				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else{
					setMemory2(dstObj.address, tmp);
				}


				cc.set((tmp << 16 >>> 31)>0, 
						tmp==0, 
						getSubOverflow(srcObj.operand, dstObj.operand, tmp), 
						!getSubBorrow(srcObj.operand, dstObj.operand, tmp));
	
				break;
			case SWAB:
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				
				tmp = (dstObj.operand << 16 >>> 24 ) + (dstObj.operand << 24 >>> 16);

				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else if(dstObj.flgAddress){
					setMemory2(dstObj.address, tmp);
				}

				cc.set((tmp << 24 >>> 31)>0, tmp << 24 >>> 24 == 0, false, false);
				cc.set(cc.n, cc.z, cc.v, cc.c);

				break;
			case SXT:
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				if(cc.n == true){
					if(dstObj.flgRegister){
						reg.set(dstObj.register, 0xffff);
					}else if(dstObj.flgAddress){
						setMemory2(dstObj.address, 0xffff);
					}
				}else{
					if(dstObj.flgRegister){
						reg.set(dstObj.register, 0);
					}else if(dstObj.flgAddress){
						setMemory2(dstObj.address, 0);
					}
				}
				
				cc.set(false,
						!cc.n, 
						false, 
						cc.c);

				break;
			case SYS:
				
				int val1;
				int val2;

				switch(getDex((opcode >> 3) & 7,opcode  & 7)){
				case 0: //systemcall
					int sub = getMem();
					tmp = reg.get(7);
					execute(sub, sub+1, true);

					reg.set(7, tmp);

					break;
				case 1: //exit
					if(flgDebugMode>0) System.out.println("\n exit:");
					int exitNo = reg.get(0);
					if(proc[nowProcessNo].flgChildProcess){
						//実行
						if(flgExeMode){
							nowProcessNo = proc[nowProcessNo].parentPid;

							proc[nowProcessNo].childExitNo = exitNo;
							reg.set(0,proc[nowProcessNo].childPid);
							reg.set(1,proc[nowProcessNo].r1);
							reg.set(2,proc[nowProcessNo].r2);
							reg.set(3,proc[nowProcessNo].r3);
							reg.set(4,proc[nowProcessNo].r4);
							reg.set(5,proc[nowProcessNo].r5);
							reg.set(6,proc[nowProcessNo].r6);
							reg.set(7,proc[nowProcessNo].r7+2);

							/*
							if(flgDebugMode>0) System.out.println("callPID=" + nowProcessNo);
							if(flgDebugMode>0) System.out.print(" PID=" + proc[nowProcessNo].pid);
							if(flgDebugMode>0) System.out.print(" childPID=" + proc[nowProcessNo].childPid);
							if(flgDebugMode>0) System.out.print(" parentPID=" + proc[nowProcessNo].parentPid);
							*/

							if(flgExeMode) execute(0, proc[nowProcessNo].vas.textSize,false,true);
						}
					}else{
						System.exit(0);
					}
					break;
				case 2: //fork
					if(flgDebugMode>0) System.out.println("\n fork:");
					
					//仮想メモリを退避
					proc[nowProcessNo].r0 = reg.get(0);
					proc[nowProcessNo].r1 = reg.get(1);
					proc[nowProcessNo].r2 = reg.get(2);
					proc[nowProcessNo].r3 = reg.get(3);
					proc[nowProcessNo].r4 = reg.get(4);
					proc[nowProcessNo].r5 = reg.get(5);
					proc[nowProcessNo].r6 = reg.get(6);
					proc[nowProcessNo].r7 = reg.get(7);
					proc[nowProcessNo].childPid = nowProcessNo+1;

					/*
					if(flgDebugMode>0) System.out.println("nowPID=" + nowProcessNo);
					if(flgDebugMode>0) System.out.print(" PID=" + proc[nowProcessNo].pid);
					if(flgDebugMode>0) System.out.print(" childPID=" + proc[nowProcessNo].childPid);
					if(flgDebugMode>0) System.out.print(" parentPID=" + proc[nowProcessNo].parentPid);
					*/

					proc[nowProcessNo+1] = (Process) proc[nowProcessNo].clone();

					nowProcessNo++;
					reg.set(0, 0);

					for(int k=3;k<16;k++) fd.clear(k);
					
					proc[nowProcessNo].flgChildProcess = true;
					proc[nowProcessNo].pid = nowProcessNo;
					proc[nowProcessNo].parentPid = nowProcessNo-1;

					/*
					if(flgDebugMode>0) System.out.println(" nowPID=" + nowProcessNo);
					if(flgDebugMode>0) System.out.print(" PID=" + proc[nowProcessNo].pid);
					if(flgDebugMode>0) System.out.print(" childPID=" + proc[nowProcessNo].childPid);
					if(flgDebugMode>0) System.out.print(" parentPID=" + proc[nowProcessNo].parentPid);
					*/
					
					//実行
					if(flgExeMode) execute(0, proc[nowProcessNo].vas.textSize,false,true);
					
					break;
				case 3: //read
					val1 = getMem(); //読み込み位置
					val2 = getMem();  //読み込みサイズ
					
					if(flgDebugMode>0) System.out.print("\n read:" + reg.get(0) + "," + val1 + "," + val2);

					if(fd.isFile(reg.get(0))){
						BlockFile inBlockFile = (BlockFile) fd.get(reg.get(0)); 
						File infile = new File(inBlockFile.inode.toString());

						//ランダムアクセスファイル取得
						RandomAccessFile inraf;
						try {
							inraf = new RandomAccessFile(infile, "r");
			        		inraf.seek(fd.getOffset(reg.get(0)));

			        		if(inraf.length() < fd.getOffset(reg.get(0))+1){
			        			reg.set(0,0);
			        		}else{
			        			int inSize = inraf.read(proc[nowProcessNo].vas.mem, val1, val2);
			        			fd.setOffset(reg.get(0),fd.getOffset(reg.get(0))+inSize);
			        			reg.set(0,inSize);
			        		}
			        		inraf.close();
			        
						} catch (FileNotFoundException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}else{

						for(int i=0;i<val2;i++){
					        int c;
					        try {
					        	int inSize = 0;
								while ((c = System.in.read()) != -1){
									if(c != 0x0d && c != 0x20){
										setMemory1(val1,c);
										val1++;
										inSize++;
										break;
									}
								}
			        			reg.set(0,inSize);
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
					
					cc.set(cc.n, cc.z, cc.v, false);
					
					break;
				case 4: //write
					val1 = getMem(); //書き込み元データ位置
					val2 = getMem(); //書き込みサイズ

					if(flgDebugMode>0) System.out.print("\n write:" + reg.get(0) + "," + val1 + "," + val2 + "," + reg.get(0) + "," + fd.getOffset(reg.get(0)));

					int i = 0;
					if(fd.isFile(reg.get(0))){
			
						BlockFile outFile = (BlockFile) fd.get(reg.get(0)); 
				        File file = new File(outFile.inode.toString());
				        
				        //書き込み内容取得
						byte[] writeByte = new byte[val2];
				        for(i=0;i<val2;i++){
					        writeByte[i] = (byte) (getMemory1(val1) << 24 >>> 24);
					        //if(i%16==0) System.out.print("\n");
					        //System.out.print(String.format("%02x", getMemory1(val1) << 24 >>> 24));
							val1++;
						}
				        
				        //ランダムアクセスファイル取得
				        RandomAccessFile raf;
				        try {
							raf = new RandomAccessFile(file, "rw");
					        raf.seek(fd.getOffset(reg.get(0)));
					        raf.write(writeByte);
					        raf.close();
					        
					        fd.setOffset(reg.get(0),fd.getOffset(reg.get(0)) + writeByte.length);
					        
						} catch (FileNotFoundException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
 						
					}else{
						for(i=0;i<val2;i++){
							printChar(getMemory1(val1));
							val1++;
						}
					}
					reg.set(0,i);

					break;
				case 5: //open
					File openFile = getFile(getMem(),"open");
					getMem();
					
					if(openFile.isFile()){
						reg.set(0,fd.open(fd.search(), openFile.toPath()));
						cc.set(cc.n, cc.z, cc.v, false);
						
					}else{
						reg.set(0,0);
						cc.set(cc.n, cc.z, cc.v, true);
					}
					
					break;
				case 6: //close
					if(flgDebugMode>0) System.out.print("\n close:" + reg.get(0));
					fd.clear(reg.get(0));
					reg.set(0, 0);
					
					break;
				case 7: //wait
					if(flgDebugMode>0) System.out.print("\n wait:");
					reg.set(0, proc[nowProcessNo].childPid);
					reg.set(1, proc[nowProcessNo].childExitNo << 8);
					
					break;
				case 8: //create
					File createFile = getFile(getMem(),"creat");
					getMem();

					if (createFile.exists()) createFile.delete();
					
					try {
						createFile.createNewFile();
					} catch (IOException e) {
						e.printStackTrace();
					}

					//TODO エラー発生しない体
					cc.set(cc.n, cc.z, cc.v, false);

					reg.set(0,fd.open(fd.search(), createFile.toPath()));
					
					break;
				case 9: //link
					File existringFile = getFile(getMem(),"unlinkFrom");
					Path existingLink = existringFile.toPath();

					File newFile = getFile(getMem(),"unlinkTo");
					Path newLink = newFile.toPath();
					
					try {
						java.nio.file.Files.createLink(newLink, existingLink);
					} catch (IOException x) {
					    System.err.println(x);
					} catch (UnsupportedOperationException x) {
					    System.err.println(x);
					}
					
					reg.set(0, 0);
					
					//TODO エラー発生しない体
					cc.set(cc.n, cc.z, cc.v, false);
					
					break;
				case 10: //unlink
					File unlinkFile = getFile(getMem(),"unlink");
					unlinkFile.delete();
					
					reg.set(0, 0);
					
					//TODO エラー発生しない体
					cc.set(cc.n, cc.z, cc.v, false);
					
					break;
				case 11: //exec
					if(flgDebugMode>0) System.out.print("\n exec:");

					//System.exit(0);
					
					String execTmp1 = getFileName(getMem());
					int argsIndex = getMem();
					ArrayList<String> execArgs = new ArrayList<String>();
					
					while(true){
						if(getMemory2(argsIndex) == 0) break;
						execArgs.add(getFileName(getMemory2(argsIndex)));
						argsIndex += 2;
					}
					
					setStack(execArgs); //スタック設定
					readBinary(execTmp1); //プロセスにバイナリを読み込み
					setExecute(flgDebugMode, flgMemoryDump, argStack); //実行前設定

					/*
					if(flgDebugMode>0) System.out.print("execPID=" + nowProcessNo);
					if(flgDebugMode>0) System.out.print(" PID=" + proc[nowProcessNo].pid);
					if(flgDebugMode>0) System.out.print(" childPID=" + proc[nowProcessNo].childPid);
					if(flgDebugMode>0) System.out.print(" parentPID=" + proc[nowProcessNo].parentPid);
					*/
					
					if(flgExeMode) execute(0, proc[nowProcessNo].vas.textSize);

					//TODO エラー発生しない体
					cc.set(cc.n, cc.z, cc.v, false);

					break;
				case 15: //chmod
					if(flgDebugMode>0) System.out.print("\n chmod");
					int chmodIndex = getMem();
					if(flgDebugMode>0) System.out.print(getFileName(chmodIndex));
					int chmodIndex2 = getMem();
					if(flgDebugMode>0) System.out.print(" " + chmodIndex2);
					reg.set(0, 0);
					
					break;
				case 17: //brk
					if(flgDebugMode>0) System.out.print("\n brk:");
					reg.set(0, 0);
					
					break;
				case 18: //stat
					File statFile = getFile(getMem(),"stat");
					
					if(flgDebugMode>0) System.out.print(" ");

					int writeMem = getMem();
					
					if(statFile.isFile()){
					    setMemory2(writeMem,0); /* ファイルがあるデバイスの ID */
					    setMemory2(writeMem+2,0); /* inode 番号 */
					    setMemory2(writeMem+4,0xb6); /* アクセス保護 */
					    setMemory2(writeMem+6,0); /* ハードリンクの数 */
					    setMemory2(writeMem+7,0); /* 所有者のユーザ ID */
					    setMemory2(writeMem+8,0); /* 所有者のグループ ID */
					    setMemory1(writeMem+9,(int)statFile.length() >> 16);  /* 全体のサイズ (バイト単位) */
					    setMemory2(writeMem+10,(int)statFile.length() << 16 >>> 16); /* 全体のサイズ (バイト単位)  */
					    setMemory2(writeMem+12,0); /* アドレス */
					    setMemory2(writeMem+28,0); /* 最終アクセス時刻 */
					    setMemory2(writeMem+30,0); /* 最終アクセス時刻 */
					    setMemory2(writeMem+32,0); /* 最終修正時刻 */ 
					    setMemory2(writeMem+34,0); /* 最終修正時刻 */
					}else{
						cc.set(cc.n, cc.z, cc.v, true);
					}
					
					break;
				case 19: //lseek
					val1 = getMem(); //シークサイズ
					val2 = getMem(); //モード
					
					if(flgDebugMode>0) System.out.print("\n lseek:" + reg.get(0) + "," + val1 + "," + val2);
					
					//mode
					//0:top byte
					//1:offset byte
					//2:end byte
					//3:top block
					//4:offset block
					//5:end block
					switch(val2){
					case 0:
						fd.setOffset(reg.get(0),0);
						fd.setOffset(reg.get(0), fd.getOffset(reg.get(0)) + val1);
						break;
					case 1:
						fd.setOffset(reg.get(0), fd.getOffset(reg.get(0)) + val1);
						break;
					case 2:
						fd.setOffset(reg.get(0), fd.getSize(reg.get(0)) + val1);
					case 3:
						fd.setOffset(reg.get(0),0);
						fd.setOffset(reg.get(0), fd.getOffset(reg.get(0)) + (val1*512));
						break;
					case 4:
						fd.setOffset(reg.get(0), fd.getOffset(reg.get(0)) + (val1*512));
						break;
					case 5:
						fd.setOffset(reg.get(0), fd.getSize(reg.get(0)) + (val1*512));
						break;
					}
					
					reg.set(0, fd.getOffset(reg.get(0)));
					cc.reset();

					break;
				case 20: //getpid
					if(flgDebugMode>0) System.out.print("\n getpid:");
					/*
				    String processName =
				    	      java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
				    int pid = (int)Long.parseLong(processName.split("@")[0]);
				    System.out.println(" " + pid);
				    */
					
					if(flgDebugMode>0) System.out.println(proc[nowProcessNo].pid);
					reg.set(0,proc[nowProcessNo].pid);

					break;
				case 41: //dup
					if(flgDebugMode>0) System.out.print("\n dup:" + reg.get(0));
					reg.set(0,fd.copy(fd.search(), reg.get(0)));

					break;
				case 48: //signal
					val1 = getMem(); //シークサイズ
					val2 = getMem(); //モード

					if(flgDebugMode>0) System.out.print("\n signal:" + reg.get(0) + "," + val1 + "," + val2);

					signal.set(val1, val2);
					reg.set(0,0);
					cc.set(cc.n,cc.z,cc.v,false);

					break;
				}
				break;
			case TST:
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				cc.set((dstObj.operand << 16 >>> 31)>0, (dstObj.operand << 16 >>> 16)==0, false, false);
				break;
			case TSTB:
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7, true);
				cc.set((dstObj.operand << 1 >>> 15)>0, (dstObj.operand << 24 >>> 24)==0, false, false);
				break;
			case XOR:
				int srcreg = reg.get((opcode >> 6) & 7);
				dstObj = getField(dstObj,(opcode >> 3) & 7,opcode  & 7);
				tmp = srcreg^(dstObj.operand);
				
				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else if(dstObj.flgAddress){
					setMemory2(dstObj.address, tmp);
				}
				
				cc.set((tmp << 16 >>> 31)>0, (tmp << 16 >>> 16)==0, false, cc.c);
				break;
			case WORD:
				System.out.print("\n");
				System.out.println("not case");
				System.out.println(getMemory2(reg.get(7)-2));
				System.exit(0);
				break;
			}
		}
	}
	

	/*ファイル関数------------------------------------------*/
	
	//ファイル返却
	File getFile(int val,String debugName){
		if(flgDebugMode>0) System.out.print("\n " + debugName); //デバッグ用
		File file = new File(getFileName(val)); //ファイル名設定
		return file;
	}
	
	//ファイル名返却
	String getFileName(int val){
		
		StringBuffer str = new StringBuffer(""); 
		StringBuffer str2 = new StringBuffer(""); 
		
		while(true){
			if(getMemory1(val)!=0){
				str.append((char)getMemory1(val));
				val = val + 1;
			}else{
				break;
			}
		}
		
		if(flgOsMode == 0){
			if(str.charAt(0) == '/' && str.charAt(1) == 't' && str.charAt(2) == 'm' && str.charAt(3) == 'p' && str.charAt(4) == '/'){
				str2.append("D:\\03.workspace\\v6tmp\\");
				str2.append(str.substring(5));
			}else if(str.charAt(0) == '/' && str.charAt(1) == 'b' && str.charAt(2) == 'i' && str.charAt(3) == 'n' && str.charAt(4) == '/'){
				str2.append("D:\\03.workspace\\v6root\\bin\\");
				str2.append(str.substring(5));
			}else if(str.charAt(0) == '/' && str.charAt(1) == 'l' && str.charAt(2) == 'i' && str.charAt(3) == 'b' && str.charAt(4) == '/'){
				str2.append("D:\\03.workspace\\v6root\\lib\\");
				str2.append(str.substring(5));
			}else if(str.charAt(0) == '.' && str.charAt(1) == '.' && str.charAt(2) == '/'){
				str2.append("D:\\03.workspace\\kernel_cmp\\v6root\\usr\\sys\\");
				str2.append(str.substring(3));
			}else{
				str2.append(str.substring(0));
			}
		}

		if(flgOsMode == 1){
			if(str.charAt(0) == '/' && str.charAt(1) != 'h') str.insert(0, "/home/zer0/v6root");
			str2.append(str.substring(0));
		}
		
		if(flgDebugMode>0) System.out.print(":" + str2.toString());
		
		return str2.toString();
	}	

	
	/*オーバーフロー関数------------------------------------------*/

	//加算オーバーフロー判定
	boolean getAddOverflow(int src, int dst, int val){
		boolean addV = false;
		if((dst << 1 >>> 16) == (src << 1 >>> 16)){
			if((dst << 1 >>> 16) != (val << 1 >>> 16)) addV = true;
		}
		return addV;
	}

	//減算オーバーフロー判定
	boolean getSubOverflow(int src, int dst, int val){
		boolean subV = false;
		if((dst << 16 >>> 31) != (src << 16 >>> 31)){
			if((dst << 16 >>> 31) == (val << 16 >>> 31)) subV = true;
		}
		return subV;
	}

	//加算キャリー判定
	boolean getAddCarry(int src, int dst, int val){
		boolean addC = false;
		if(((src << 16) >>> 31) == 1){
			if(((dst << 16) >>> 31) == 1){
				addC = true;
			}else{
				if(((val << 16) >>> 31) == 0) addC = true;
			}
		}else{
			if(((dst << 16) >>> 31) == 1){
				if(((val << 16) >>> 31) == 0) addC = true;
			}
		}
		return addC;
	}
	
	//減算ボロー判定
	boolean getSubBorrow(int src, int dst, int val){
		boolean subC = false;
		if(((src << 16) >>> 31) == 0){
			if(((dst << 16) >>> 31) == 1){
				subC = true;
			}else{
				if(((val << 16) >>> 31) == 1) subC = true;
			}
		}else{
			if(((dst << 16) >>> 31) == 1){
				if(((val << 16) >>> 31) == 1) subC = true;
			}
		}
		return subC;
	}


	/*フィールド関数------------------------------------------*/
	
	//フィールド取得（PC+オフセット*2 8bit（符号付））
	FieldDto getOffset(FieldDto operand,int first,int second,int third){
		operand.reset();
		int tmp = (first << 6) + (second << 3) + third;
		byte tmpByte = (byte)tmp;
		tmp = reg.get(7) + tmpByte * 2;
		
		if(!flgExeMode) operand.setStr("0x" + String.format("%x",tmp));
		operand.setAddress(tmp);

		return operand;
	}

	//フィールド取得（PC-オフセット*2 6bit（符号なし、正の数値））
	FieldDto getOffset6(FieldDto operand,int first,int second){
		operand.reset();
		int tmp = (first << 3) + second;
		tmp = reg.get(7) - tmp * 2;

		if(!flgExeMode) operand.setStr("0x" + String.format("%x",tmp));
		operand.setAddress(tmp);

		return operand;
	}

	//フィールド取得（8進数 6bit）
	FieldDto getNormal(FieldDto operand,int first,int second,int third){
		operand.reset();
		if(!flgExeMode) operand.setStr(String.format("%o",(first << 6) + (second << 3) + third));
		operand.setAddress(((first << 3) + second) * 2 + reg.get(7));

		return operand;
	}

	//フィールド取得（dst,src）
	FieldDto getField(FieldDto field,int mode, int regNo){
		return getField(field, mode, regNo, false);
	}

	//フィールド取得（dst,src）
	FieldDto getField(FieldDto field,int mode, int regNo, boolean byteFlg){
		field.reset();
		
		//ワーク
		short opcodeShort;
		int opcodeInt;
		int tmp;

		switch(regNo){
		case 0:
		case 1:
		case 2:
		case 3:
		case 4:
		case 5:
		case 6:
			switch(mode){
			case 0:
				//レジスタ
				//registerにオペランドがある。
				if(flgExeMode){
					field.setOperand(reg.get(regNo));
					field.setReg(regNo);
				}else{
					field.setStr(getRegisterName(regNo));
				}
				break;
			case 1:
				//レジスタ間接
				//registerにオペランドのアドレスがある。
				if(flgExeMode){
					if(byteFlg){
						field.setOperand(getMemory1(reg.get(regNo)));
						field.setAddress(reg.get(regNo));
					}else{
						field.setOperand(getMemory2(reg.get(regNo)));
						field.setAddress(reg.get(regNo));
					}
				}else{
					field.setStr("(" + getRegisterName(regNo) + ")");
				}
				break;
			case 2:
				//自動インクリメント
				//registerにオペランドのアドレスがあり、命令実行後にregisterの内容をインクリメントする。
				if(flgExeMode){
					if(byteFlg){
						field.setOperand(getMemory1(reg.get(regNo)));
						field.setAddress(reg.get(regNo));
						if(regNo==6){
							reg.add(regNo,2);
						}else{
							reg.add(regNo,1);
						}
					}else{
						field.setOperand(getMemory2(reg.get(regNo)));
						field.setAddress(reg.get(regNo));
						reg.add(regNo,2);
					}
				}else{
					field.setStr("(" + getRegisterName(regNo) + ")+");
				}
				break;
			case 3:
				//自動インクリメント間接
				//registerにオペランドへのポインタのアドレスがあり、命令実行後にregisterの内容を2だけインクリメントする。
				if(flgExeMode){
					field.setOperand(getMemory2(getMemory2(reg.get(regNo))));
					field.setAddress(getMemory2(reg.get(regNo)));
					reg.add(regNo,2);
				}else{
					field.setStr("*(" + getRegisterName(regNo) + ")+");
				}
				break;
			case 4:
				//自動デクリメント
				//命令実行前にregisterをデクリメントし、それをオペランドのアドレスとして使用する。
				if(flgExeMode){
					if(byteFlg){
						if(regNo==6){
							reg.add(regNo,-2);
						}else{
							reg.add(regNo,-1);
						}
						field.setOperand(getMemory1(reg.get(regNo)));
						field.setAddress(reg.get(regNo));
					}else{
						reg.add(regNo,-2);
						field.setOperand(getMemory2(reg.get(regNo)));
						field.setAddress(reg.get(regNo));
					}
				}else{
					field.setStr("-(" + getRegisterName(regNo) + ")");
				}
				break;
			case 5:
				//自動デクリメント間接
				//命令実行前にregisterを2だけデクリメントし、それをオペランドへのポインタのアドレスとして使用する。
				if(flgExeMode){
					//reg.add(regNo,-4);
					reg.add(regNo,-2);
					field.setOperand(getMemory2(getMemory2(reg.get(regNo))));
					field.setAddress(getMemory2(reg.get(regNo)));
				}else{
					field.setStr("*-(" + getRegisterName(regNo) + ")");
				}
				break;
			case 6:
				//インデックス
				//register+Xがオペランドのアドレス。Xはこの命令に続くワード。
				opcodeShort = (short)getMem();

				if(flgExeMode){
					if(byteFlg){
						field.setOperand(getMemory1(reg.get(regNo) + opcodeShort));
						field.setAddress(reg.get(regNo) + opcodeShort);
					}else{
						field.setOperand(getMemory2(reg.get(regNo) + opcodeShort));
						field.setAddress(reg.get(regNo) + opcodeShort);
						
					}
				}else{
					if(opcodeShort < 0){
						field.setStr("-" + String.format("%o",~(opcodeShort - 1)) + "(" + getRegisterName(regNo) + ")");
					}else{
						field.setStr(String.format("%o",opcodeShort) + "(" + getRegisterName(regNo) + ")");
					}
				}
				break;
			case 7:
				//インデックス間接
				//register+Xがオペランドへのポインタのアドレス。Xはこの命令に続くワード。
				opcodeShort = (short)getMem();

				if(flgExeMode){
					field.setOperand(getMemory2(getMemory2(reg.get(regNo) + opcodeShort)));
					field.setAddress(getMemory2(reg.get(regNo) + opcodeShort));
				}else{
					if(opcodeShort < 0){
						field.setStr("*-" + String.format("%o",~(opcodeShort - 1)) + "(" + getRegisterName(regNo) + ")");
					}else{
						field.setStr("*-" + String.format("%o",opcodeShort) + "(" + getRegisterName(regNo) + ")");
					}
				}
				break;
			}
			break;

		case 7:
			switch(mode){
			case 0:
				//レジスタ
				//registerにオペランドがある。
				if(flgExeMode){
					field.setOperand(reg.get(regNo));
					field.setReg(regNo);
				}else{
					field.setStr(getRegisterName(regNo));
				}
				break;
			case 1:
				//レジスタ間接
				//registerにオペランドのアドレスがある。
				
				if(flgExeMode){
					if(byteFlg){
						field.setOperand(getMemory1(reg.get(regNo)));
						field.setAddress(reg.get(regNo));
					}else{
						field.setOperand(getMemory2(reg.get(regNo)));
						field.setAddress(reg.get(regNo));
					}
				}else{
					field.setStr("(" + getRegisterName(regNo) + ")");
				}
				break;			
			case 2:
				//イミディエート
				//オペランドは命令内にある。
				opcodeShort = (short)getMem();

				if(flgExeMode){
					field.setOperand((int)opcodeShort);
				}else{
					if(opcodeShort < 0){
						field.setStr("$" + "-" + String.format("%o",~(opcodeShort - 1)));
					}else{
						field.setStr("$" + String.format("%o",opcodeShort));
					}
				}
				break;
			case 3:
				//絶対
				//オペランドの絶対アドレスが命令内にある。
				opcodeShort = (short)getMem();

				if(flgExeMode){
					field.setOperand((int)opcodeShort); //未検証
					field.setAddress((int)opcodeShort);
				}else{
					if(opcodeShort < 0){
						field.setStr("*$" + "-" + String.format("%o",~(opcodeShort - 1)));
					}else{
						field.setStr("*$" + String.format("%o",opcodeShort));
					}
				}
				break;
			case 4:
				//自動デクリメント
				//命令実行前にregisterをデクリメントし、それをオペランドのアドレスとして使用する。
				if(flgExeMode){
					if(byteFlg){
						reg.add(regNo,-2);
						//reg.add(regNo,-1);
						field.setOperand(getMemory1(reg.get(regNo)));
						field.setAddress(reg.get(regNo));
					}else{
						reg.add(regNo,-2);
						field.setOperand(getMemory2(reg.get(regNo)));
						field.setAddress(reg.get(regNo));
					}
				}else{
					field.setStr("-(" + getRegisterName(regNo) + ")");
				}
				break;
			case 5:
				//自動デクリメント間接
				//命令実行前にregisterを2だけデクリメントし、それをオペランドへのポインタのアドレスとして使用する。
				if(flgExeMode){
					reg.add(regNo,-4);
					field.setOperand(getMemory2(getMemory2(reg.get(regNo))));
					field.setAddress(getMemory2(reg.get(regNo)));
				}else{
					field.setStr("*-(" + getRegisterName(regNo) + ")");
				}
				break;
			case 6:
				//相対
				//命令に続くワードの内容 a を PC+2 に加算したものをアドレスとして使用する。
				opcodeShort = (short)getMem();
				tmp = opcodeShort + reg.get(7);
				
				if(flgExeMode){
					field.setOperand(getMemory2(tmp));
					field.setAddress(tmp);
				}else{
					field.setStr("0x" + String.format("%02x",tmp));
					
				}
				break;
			case 7:
				//相対間接
				//命令に続くワードの内容 a を PC+2 に加算したものをアドレスのアドレスとして使用する。
				opcodeInt = (int)getMem() << 16 >>> 16;
				
				tmp = opcodeInt + reg.get(7);

				if(!flgExeMode) field.setStr("*$0x" + String.format("%02x",(tmp)));
				field.setOperand(getMemory2(getMemory2(tmp))); //TODO
				field.setAddress(getMemory2(tmp));
				break;
			}
			break;
		}	
		return field;
	}

	
	/*ニーモニック関数------------------------------------------*/

	Mnemonic getMnemonic(int opcode){
		Mnemonic mnemonic = null;

		switch(opcode >> 15){
		case 0:
			switch((opcode >> 12) & 7){
			case 0:
				switch((opcode >> 9) & 7){
				case 0:
					switch((opcode >> 6) & 7){
					case 0:
						switch((opcode >> 3) & 7){
						case 0:
							switch(opcode  & 7){
							case 6:
								mnemonic = Mnemonic.RTT;
								break;
							}
							break;
						}
						break;
					case 1:
						mnemonic = Mnemonic.JMP;
						break;
					case 2:
						switch((opcode >> 3) & 7){
						case 0:
							mnemonic = Mnemonic.RTS;
							break;
						case 6:
							switch(opcode  & 7){
							case 2:
								mnemonic = Mnemonic.SEV;
								break;
							}
							break;
						}
						break;
					case 3:
						mnemonic = Mnemonic.SWAB;
						break;
					case 4:
					case 5:
					case 6:
					case 7:
						mnemonic = Mnemonic.BR;
						break;
					}
					break;
				case 1:
					switch((opcode >> 6) & 7){
					case 0:
					case 1:
					case 2:
					case 3:
						mnemonic = Mnemonic.BNE;
						break;
					case 4:
					case 5:
					case 6:
					case 7:
						mnemonic = Mnemonic.BEQ;
						break;
					}
					break;
				case 2:
					switch((opcode >> 6) & 7){
					case 0:
					case 1:
					case 2:
					case 3:
						mnemonic = Mnemonic.BGE;
						break;
					case 4:
					case 5:
					case 6:
					case 7:
						mnemonic = Mnemonic.BLT;
						break;
					}
					break;
				case 3:
					switch((opcode >> 6) & 7){
					case 0:
					case 1:
					case 2:
					case 3:
						mnemonic = Mnemonic.BGT;
						break;
					case 4:
					case 5:
					case 6:
					case 7:
						mnemonic = Mnemonic.BLE;
						break;
					}
					break;
				case 4:
					mnemonic = Mnemonic.JSR;
					break;
				case 5:
					switch((opcode >> 6) & 7){
					case 0:
						mnemonic = Mnemonic.CLR;
						break;
					case 1:
						mnemonic = Mnemonic.COM;
						break;
					case 2:
						mnemonic = Mnemonic.INC;
						break;
					case 3:
						mnemonic = Mnemonic.DEC;
						break;
					case 4:
						mnemonic = Mnemonic.NEG;
						break;
					case 5:
						mnemonic = Mnemonic.ADC;
						break;
					case 7:
						mnemonic = Mnemonic.TST;
						break;
					}
					break;
				case 6:
					switch((opcode >> 6) & 7){
					case 0:
						mnemonic = Mnemonic.ROR;
						break;
					case 1:
						mnemonic = Mnemonic.ROL;
						break;
					case 2:
						mnemonic = Mnemonic.ASR;
						break;
					case 3:
						mnemonic = Mnemonic.ASL;
						break;
					case 7:
						mnemonic = Mnemonic.SXT;
						break;
					}
					break;
				}
				break;
			case 1:
				mnemonic = Mnemonic.MOV;
				break;
			case 2:
				mnemonic = Mnemonic.CMP;
				break;
			case 3:
				mnemonic = Mnemonic.BIT;
				break;
			case 4:
				mnemonic = Mnemonic.BIC;
				break;
			case 5:
				mnemonic = Mnemonic.BIS;
				break;
			case 6:
				mnemonic = Mnemonic.ADD;
				break;
			case 7:
				switch((opcode >> 9) & 7){
				case 0:
					mnemonic = Mnemonic.MUL;
					break;
				case 1:
					mnemonic = Mnemonic.DIV;
					break;
				case 2:
					mnemonic = Mnemonic.ASH;
					break;
				case 3:
					mnemonic = Mnemonic.ASHC;
					break;
				case 4:
					mnemonic = Mnemonic.XOR;
					break;
				case 7:
					mnemonic = Mnemonic.SOB;
					break;
				}
				break;
			}
			break;
		case 1:
			switch((opcode >> 12) & 7){
			case 0:
				switch((opcode >> 9) & 7){
				case 0:
					switch((opcode >> 6) & 7){
					case 0:
					case 1:
					case 2:
					case 3:
						mnemonic = Mnemonic.BPL;
						break;
					case 4:
					case 5:
					case 6:
					case 7:
						mnemonic = Mnemonic.BMI;
						break;
					}
					break;
				case 1:
					switch((opcode >> 6) & 7){
					case 0:
					case 1:
					case 2:
					case 3:
						mnemonic = Mnemonic.BHI;
						break;
					case 4:
					case 5:
					case 6:
					case 7:
						mnemonic = Mnemonic.BLOS;
						break;
					}
					break;
				case 2:
					switch((opcode >> 6) & 7){
					case 4:
					case 5:
					case 6:
					case 7:
						mnemonic = Mnemonic.BVS;
						break;
					}
					break;
				case 3:
					switch((opcode >> 6) & 7){
					case 0:
						mnemonic = Mnemonic.BCC;
						break;
					case 4:
					case 5:
					case 6:
					case 7:
						mnemonic = Mnemonic.BCS;
						break;
					}
					break;
				case 4:
					switch((opcode >> 6) & 7){
					case 4:	
						mnemonic = Mnemonic.SYS;
						break;
					}
					break;
				case 5:
					switch((opcode >> 6) & 7){
					case 0:
						mnemonic = Mnemonic.CLRB;
						break;
					case 2:
						mnemonic = Mnemonic.INCB;
						break;
					case 3:
						mnemonic = Mnemonic.DECB;
						break;
					case 7:
						mnemonic = Mnemonic.TSTB;
						break;
					}
					break;
				}
				break;
			case 1:
				mnemonic = Mnemonic.MOVB;
				break;
			case 2:
				mnemonic = Mnemonic.CMPB;
				break;
			case 3:
				mnemonic = Mnemonic.BITB;
				break;
			case 4:
				mnemonic = Mnemonic.BICB;
				break;
			case 5:
				mnemonic = Mnemonic.BISB;
				break;
			case 6:
				mnemonic = Mnemonic.SUB;
				break;
			case 7:
				mnemonic = Mnemonic.SETD;
				break;
			}
			break;
		}
		if(mnemonic == null) mnemonic = Mnemonic.WORD;

		return mnemonic;
	}
	

	/*出力関数------------------------------------------*/

	//レジスタ・フラグの出力
	void printDebug(){
		System.out.print("\n");

		System.out.print(String.format("%04x",reg.get(0) << 16 >>> 16));
		System.out.print(" " + String.format("%04x",reg.get(1) << 16 >>> 16));
		System.out.print(" " + String.format("%04x",reg.get(2) << 16 >>> 16));
		System.out.print(" " + String.format("%04x",reg.get(3) << 16 >>> 16));
		System.out.print(" " + String.format("%04x",reg.get(4) << 16 >>> 16));
		System.out.print(" " + String.format("%04x",reg.get(5) << 16 >>> 16));
		System.out.print(" " + String.format("%04x",reg.get(6) << 16 >>> 16));

		
		System.out.print(" " + String.format("%04x",getMemory2(0xff5e)));
		//System.out.print(" " + String.format("%04x",getMemory2(0x11b0)));
		
		//System.out.print(" " + String.format("%04x",getMemory2(0x2026)));
		System.out.print(" ");

		if(cc.z){
			System.out.print("Z");
		}else{
			System.out.print("-");
		}
		if(cc.n){
			System.out.print("N");
		}else{
			System.out.print("-");
		}
		if(cc.c){
			System.out.print("C");
		}else{
			System.out.print("-");
		}
		if(cc.v){
			System.out.print("V");
		}else{
			System.out.print("-");
		}

		System.out.print(" ");
		System.out.print(String.format("%04x",reg.get(7)));
		System.out.print(":");
	}

	//メモリダンプの出力
	void printMemory(){
		System.out.print("\n--memory-start-------------");
		for(int m=0x14d0;m<0x14ef;m=m+2){
			if(m%16==0) System.out.print(String.format("\n%02x:",m/16));
			System.out.print(" " + String.format("%04x",getMemory2(m)));
		}
		System.out.println("\n--memory-end-------------");
	}

	

}