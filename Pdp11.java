package pdp11;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.Stack;

public class Pdp11{

	public static void main(String[] args){
		//オプション
		boolean flgDism = false;
		int flgDebug = 0;
		boolean flgExe = false;
		boolean flgMmr = false;

		//オプション設定
		int i = 0;
		while(true){
			if(!(args[i].substring(0,1).equals("-"))){
				break;
			}
			//レジスタ・フラグ
			if(args[i].equals("-s")){
				flgDebug = 1;
			}
			//レジスタ・フラグ
			if(args[i].equals("-v")){
				flgDebug = 2;
			}
			//逆アセンブル
			if(args[i].equals("-d")){
				flgDism = true;
			}
			//実行
			if(args[i].equals("-e")){
				flgExe = true;
			}
			//メモリダンプ
			if(args[i].equals("-m")){
				flgMmr = true;
			}
			i++;
		}
		
		//オプション指定がなければ逆アセンブルと実行
		if(flgDebug==0 && !flgDism && !flgExe && !flgMmr){
			flgDism = true;
			flgExe = true;
		}
		
		//ファイル名設定
		File file = new File(args[i]);
		Path fileName = file.toPath();
		
		//引数設定
		ArrayList<byte[]> arg = new ArrayList<byte[]>();
		int argSize = 0;
		int argCnt = 0;
		for(;i<args.length;i++){
			arg.add(args[i].getBytes());
			argSize = argSize + arg.get(argCnt).length + 1;
			argCnt++;
		}
		Stack<Byte> argStack = new Stack<Byte>();
		for(int j=0;j<arg.size();j++){
			for(int k=0;k<arg.get(j).length;k++){
				argStack.push(arg.get(j)[k]);
			}
			argStack.push((byte)0);
		}
		if(argSize%2!=0){
			argStack.push((byte)0);
		}

		//ファイル内容取得
		byte[] bf = null;
		try {
	        bf = java.nio.file.Files.readAllBytes(fileName);
		} catch (IOException e) {
			e.printStackTrace();
		}

		//仮想メモリにロード
		VirtualAddressSpace vas = new VirtualAddressSpace(bf);

		//逆アセンブル
		if(flgDism){
			vas.disassemble(0,vas.textSize);
		}

		//実行前設定
		vas.reset(flgDebug, flgMmr, argStack);

		//実行
		if(flgExe){
			vas.execute(0, vas.textSize);
		}
	}
}



/*
 * 仮想アドレス空間クラス
 */
class VirtualAddressSpace implements Cloneable{

	//仮想メモリ
	byte[] mem;
	//メモリサイズ
	int memorySize = 65536;

	//レジスタ
	Register reg;
	
	//ファイルディスクリプタ
	FileDescriptor fd;

	//コンディションコード
	ConditionCode cc;
	
	//シグナル
	Signal signal;

	//出力用ユーティリティ
	int strnum;
	
	//プロセスID
	int pid;

	//
	boolean childFlg;
	int parentPc;
	
	//実行モード
	boolean exeFlg;
	//レジスタ・フラグモード
	int dbgFlg;
	//メモリダンプモード
	boolean mmrFlg;

	//領域の大きさ
	int headerSize = 16;
	int textSize;
	int dataSize;
	int bssSize;

	//マジックナンバー
	int magicNo;
	
	//親プロセス
	VirtualAddressSpace pva;
	
	public Object clone() {
		Register defReg = new Register();
		defReg = (Register)reg.clone();
		byte[] mem2 = new byte[mem.length];
		mem2 = mem.clone();
	    return new VirtualAddressSpace(defReg,mem2);  
	}
	
	VirtualAddressSpace(Register befReg,byte[] befmem){
		reg = befReg;
		mem = befmem;
	}
	
	//コンストラクタ
	VirtualAddressSpace(byte[] bf){

		//マジックナンバーを取得
		magicNo = ((int)bf[1] & 0xFF)|(((int)bf[2] & 0xFF) << 8);

		//サイズを取得
		textSize = ((int)bf[2] & 0xFF)|(((int)bf[3] & 0xFF) << 8);
		dataSize = ((int)bf[4] & 0xFF)|(((int)bf[5] & 0xFF) << 8);
		bssSize = ((int)bf[6] & 0xFF)|(((int)bf[7] & 0xFF) << 8);

		//メモリ初期化
		mem = new byte[memorySize];
		int i;
		int cnt = 0;

		//テキスト領域読み込み
		for(i=headerSize;i<headerSize+textSize;i++){
			mem[cnt] = bf[i];
			cnt++;
		}

		//データ領域読み込み
		for(;i<headerSize+textSize+dataSize;i++){
			mem[cnt] = bf[i];
			cnt++;
		}

		//その他のメモリ初期化
		for(;cnt<memorySize;cnt++){
			mem[cnt] = 0;
		}

		//レジスタ初期化
		reg = new Register();

		//ファイルディスクリプタ初期化
		fd = new FileDescriptor();
		
		//コンディションコード初期化
		cc = new ConditionCode();
		
		//シグナル初期化
		signal = new Signal();
		
		//プロセスID初期化
		pid = 256;
		
		//
		childFlg = false;
	}

	public VirtualAddressSpace() {
		// TODO Auto-generated constructor stub
	}

	//2バイト単位でリトルエンディアンを反転して10進数で取得
	int getMemory2(int start){
		return (int) ((int)(mem[start]) & 0xFF)|( (int)((mem[start+1] & 0xFF) << 8));
	}

	//1バイト単位で指定箇所のメモリを取得
	int getMemory1(int start){
		return mem[start];
	}

	//2バイト単位で指定箇所のメモリを更新
	void setMemory2(int add,int src){
		mem[add] = (byte)src;
		mem[add+1] = (byte)(src >> 8);
	}

	//1バイト単位で指定箇所のメモリを更新
	void setMemory1(int add,int src){
		mem[add] = (byte)src;
	}

	
	//8進数に変換した命令の任意の箇所を取得
	int getOctal(int dec,int index){
		int val = Integer.parseInt(String.format("%06o",dec).substring(index, index+1));
		return val;
	}

	//ASCIIコードに変換したデータを表示
	void printChar(int dec){
		System.out.print((char)Integer.parseInt(String.format("%02x",dec),16));
	}

	//メモリ上のデータを取得して、PC+2する
	int getMem(){
		System.out.print(" mem=" + mem[1]);
		System.out.print(" reg=" + reg.get(7));
		System.out.print("getmem=" + getMemory2(reg.get(7)));
		
		int opcode = getMemory2(reg.get(7));

		System.out.print("debug");

		//逆アセンブルの場合は出力
		if(exeFlg){
			if(dbgFlg>1) printOpcode(opcode);
		}else{
			printOpcode(opcode);
			strnum++;
		}

		//PC+2
		reg.add(7,2);

		System.out.print("debug999");

		return opcode;
	}

	//スタック積む
	void pushStack(int n){
		reg.add(6,-2);
		setMemory2(reg.get(6),n);
	}

	//指定した命令を出力
	void printOpcode(int opcode){
		System.out.print(String.format("%04x", opcode));
		System.out.print(" ");
	}

	//逆アセンブル
	void disassemble(int start, int end){

		//実行モードオフ
		exeFlg = false;

		//レジスタ初期化
		reg.reset();

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
			case RTT:
				mnemonic = "rtt";
				srcOperand = "";
				dstOperand = "";
				break;
			case JMP:
				mnemonic = "jmp";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case RTS:
				mnemonic = "rts";
				srcOperand = getRegisterName(getOctal(opcode,5));
				dstOperand = "";
				break;
			case SEV:
				mnemonic = "sev";
				srcOperand = "";
				dstOperand = "";
				break;
			case SWAB:
				mnemonic = "swab";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case JSR:
				mnemonic = "jsr";
				srcOperand = getRegisterName(getOctal(opcode,3));
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case CLR:
				mnemonic = "clr";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case CLRB:
				mnemonic = "clrb";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case INC:
				mnemonic = "inc";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case INCB:
				mnemonic = "incb";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case DEC:
				mnemonic = "dec";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case DECB:
				mnemonic = "decb";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case ADC:
				mnemonic = "adc";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case ROR:
				mnemonic = "ror";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case ROL:
				mnemonic = "rol";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case TSTB:
				mnemonic = "tstb";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case TST:
				mnemonic = "tst";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case MOV:
				mnemonic = "mov";
				srcOperand = getField(getOctal(opcode,2),getOctal(opcode,3)).str;
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case MOVB:
				mnemonic = "movb";
				srcOperand = getField(getOctal(opcode,2),getOctal(opcode,3)).str;
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case CMPB:
				mnemonic = "cmpb";
				srcOperand = getField(getOctal(opcode,2),getOctal(opcode,3)).str;
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case CMP:
				mnemonic = "cmp";
				srcOperand = getField(getOctal(opcode,2),getOctal(opcode,3)).str;
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BIT:
				mnemonic = "bit";
				srcOperand = getField(getOctal(opcode,2),getOctal(opcode,3)).str;
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BITB:
				mnemonic = "bitb";
				srcOperand = getField(getOctal(opcode,2),getOctal(opcode,3)).str;
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BIS:
				mnemonic = "bis";
				srcOperand = getField(getOctal(opcode,2),getOctal(opcode,3)).str;
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BISB:
				mnemonic = "bisb";
				srcOperand = getField(getOctal(opcode,2),getOctal(opcode,3)).str;
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case ADD:
				mnemonic = "add";
				srcOperand = getField(getOctal(opcode,2),getOctal(opcode,3)).str;
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case NEG:
				mnemonic = "neg";
				srcOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case ASL:
				mnemonic = "asl";
				srcOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case ASR:
				mnemonic = "asr";
				srcOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case SXT:
				mnemonic = "sxt";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case SOB:
				mnemonic = "sob";
				srcOperand = getRegisterName(getOctal(opcode,3));
				dstOperand = getOffset6(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BR:
				mnemonic = "br";
				srcOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case BCC:
				mnemonic = "bcc";
				srcOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case BNE:
				mnemonic = "bne";
				srcOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case BEQ:
				mnemonic = "beq";
				srcOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case BGE:
				mnemonic = "bge";
				srcOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case BGT:
				mnemonic = "bgt";
				srcOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case BHI:
				mnemonic = "bhi";
				srcOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case BLE:
				mnemonic = "ble";
				srcOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case BLT:
				mnemonic = "blt";
				srcOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case BIC:
				mnemonic = "bic";
				srcOperand = getField(getOctal(opcode,2),getOctal(opcode,3)).str;
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BICB:
				mnemonic = "bicb";
				srcOperand = getField(getOctal(opcode,2),getOctal(opcode,3)).str;
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BLOS:
				mnemonic = "blos";
				srcOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case BCS:
				mnemonic = "bcs";
				srcOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case BVS:
				mnemonic = "bvs";
				srcOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case BMI:
				mnemonic = "bmi";
				srcOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case BPL:
				mnemonic = "bpl";
				srcOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case SYS:
				if(getDex(getOctal(opcode,4),getOctal(opcode,5)) == 0){
					getMem();
				}

				mnemonic = "sys";
				srcOperand = String.valueOf(getOctal(opcode,5));
				dstOperand = "";

				break;
			case SUB:
				mnemonic = "sub";
				srcOperand = getField(getOctal(opcode,2),getOctal(opcode,3)).str;
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case DIV:
				mnemonic = "div";
				srcOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = getRegisterName(getOctal(opcode,3));
				break;
			case MUL:
				mnemonic = "mul";
				srcOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = getRegisterName(getOctal(opcode,3));
				break;
			case ASH:
				mnemonic = "ash";
				srcOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = getRegisterName(getOctal(opcode,3));
				break;
			case ASHC:
				mnemonic = "ashc";
				srcOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = getRegisterName(getOctal(opcode,3));
				break;
			case SETD:
				mnemonic = "setd";
				srcOperand = "";
				dstOperand = "";
				break;
			case WORD:
				mnemonic = ".word";
				srcOperand = getNormal(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			}

			//出力
			for(;strnum<3;strnum++){
				System.out.print("     ");
			}
			System.out.print(" ");
			System.out.print(mnemonic);

			for(int j=mnemonic.length();j<9;j++){
				System.out.print(" ");
			}

			System.out.print(srcOperand);
			if(!(srcOperand.equals("")) && !(dstOperand.equals(""))){
				System.out.print(", ");
			}
			System.out.print(dstOperand + "\n");
		}
	}

	//インタプリタ実行前設定
	public void reset(int debugFlg,boolean memoryFlg, Stack<Byte> args){
		//デバッグフラグ
		dbgFlg = debugFlg;
		mmrFlg = memoryFlg;

		//レジスタ初期化
		reg.reset();

		//コンディションコード初期化
		cc.reset();
		
		//シグナル初期化
		signal.reset();

		//引数設定
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

	//インタプリタ
	public void execute(int start, int end){
		execute(start, end, false,false);
	}

	//インタプリタ
	public void execute(int start, int end,boolean endFlg){
		execute(start, end, endFlg,false);
	}

	//インタプリタ
	public void execute(int start, int end, boolean endFlg,boolean forkFlg){

		System.out.print("debug1");

		//実行モードオン
		exeFlg = true;
		
		//PCを初期化
		if(!forkFlg) reg.set(7,start);

		System.out.print("debug2");

		if(!endFlg) end = 65536;
		for(;reg.get(7)<end;){

			System.out.print("debug3");
			System.out.print(" dbgFlg=" + dbgFlg);

			//レジスタ・フラグ出力
			if(dbgFlg>1) printDebug();
			//メモリダンプ出力
			if(mmrFlg) printMemory();

			System.out.print("debug4");

			//ワーク
			FieldDto srcObj;
			FieldDto dstObj;
			int tmp = 0;

			System.out.print("debug5");
			System.out.print(" aaa=" + reg.get(7));

			//命令取得
			int opcode = getMem();

			System.out.print("debug6");

			//ニーモニック取得
			Mnemonic nic = getMnemonic(opcode);

			
			switch(nic){
			case RTT:
				break;
			case JMP:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));

				if(dstObj.flgRegister){
					reg.set(7,reg.get(dstObj.register));
				}else if(dstObj.flgAddress){
					reg.set(7,dstObj.address);
				}else{
					reg.set(7,dstObj.operand);
				}
				
				cc.set(cc.n, cc.z, cc.v, cc.c);

				break;
			case RTS:
				reg.set(7,reg.get(getOctal(opcode,5)));
				reg.set(getOctal(opcode,5),getMemory2(reg.get(6)));
				reg.add(6,2);
				
				cc.set(cc.n, cc.z, cc.v, cc.c);

				break;
			case SEV:
			
				cc.set(cc.n, cc.z, true, cc.c);

				break;
			case SWAB:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				
				tmp = (dstObj.operand << 16 >>> 24 ) + (dstObj.operand << 24 >>> 16);

				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else if(dstObj.flgAddress){
					setMemory2(dstObj.address, tmp);
				}

				cc.set((tmp << 24 >>> 31)>0, tmp << 24 >>> 24 == 0, false, false);

				cc.set(cc.n, cc.z, cc.v, cc.c);

				break;
			case JSR:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				pushStack(reg.get(getOctal(opcode,3)));
				reg.set(getOctal(opcode,3),reg.get(7));
				reg.set(7, dstObj.address);

				cc.set(cc.n, cc.z, cc.v, cc.c);

				break;
			case ROR:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				int rortmp = 0;
				if(cc.c) rortmp = 1;
				if(dstObj.operand << 31 >>> 31 == 1) cc.c = true;
				if(dstObj.operand << 31 >>> 31 == 0) cc.c = false;
				
				if(dstObj.flgRegister){
					tmp = (rortmp << 15) + (reg.get(dstObj.register) >> 1);
					reg.set(dstObj.register, tmp);
				}else if(dstObj.flgAddress){
					tmp =  (rortmp << 15) + (getMemory2(dstObj.address) >> 1);
					setMemory2(dstObj.address, tmp);
				}else{
					tmp =  (rortmp << 15) + (dstObj.operand >> 1);
					setMemory2(dstObj.address, tmp);
				}

				cc.set(cc.n, cc.z, cc.v, cc.c);

				break;
			case ROL:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
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
			case CLR:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				if(dstObj.flgRegister){
					reg.set(dstObj.register, 0);
				}else{
					setMemory2(dstObj.address,0);
				}
				
				cc.set(false, true, false, false);
				
				break;
			case CLRB:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5), true);
				if(dstObj.flgRegister){
					reg.set(dstObj.register, 0);
				}else{
					setMemory1(dstObj.address,0);
				}
				
				cc.set(false, true, false, false);
				
				break;
			case INC:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
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
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5),true);
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
			case DEC:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				
				//System.out.print(" bef=" + dstObj.operand);
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

				//System.out.print(" af=" + tmp);

				cc.set((tmp << 16 >>> 31)>0, tmp==0, cc.v, cc.c);
				
				break;
			case DECB:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5), true);
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
			case ADC:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				
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
			case TSTB:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5), true);
				cc.set((dstObj.operand << 1 >>> 15)>0, (dstObj.operand << 24 >>> 24)==0, false, false);

				break;
			case TST:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				cc.set((dstObj.operand << 16 >>> 31)>0, (dstObj.operand << 16 >>> 16)==0, false, false);
				
				break;
			case MOVB:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3), true);
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5), true);
				
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
			case MOV:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));

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
			case CMPB:
				//System.out.print(" cmpb in ");
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3), true);
				//System.out.print(" src=" + srcObj.operand);
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5), true);
				
				//System.out.print(" dst=" + dstObj.operand);

				tmp = (srcObj.operand << 24 >>> 24) - (dstObj.operand << 24 >>> 24);
				
				cc.set((tmp << 1 >>> 16)>0, 
						tmp==0, 
						getSubOverflow(srcObj.operand << 24 >>> 24, dstObj.operand << 24 >>> 24, tmp), 
						getSubBorrow(srcObj.operand << 24 >>> 24, dstObj.operand << 24 >>> 24, tmp));

				break;
			case CMP:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				tmp = (srcObj.operand << 16 >>> 16) - (dstObj.operand << 16 >>> 16);
				
				cc.set((tmp << 16 >>> 31)>0, 
						tmp==0, 
						getSubOverflow(srcObj.operand, dstObj.operand, tmp), 
						getSubBorrow(srcObj.operand, dstObj.operand, tmp));

				break;
			case BIT:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				tmp = srcObj.operand & dstObj.operand;
				
				cc.set(false, //後で書く 
						tmp==0, 
						false, 
						cc.c);

				break;
			case BITB:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				tmp = srcObj.operand & dstObj.operand;
				tmp = tmp << 24 >>> 24;
				
				//System.out.print(" src=" + srcObj.operand);
				//System.out.print(" dst=" + dstObj.operand);
				//System.out.print(" tmp=" + tmp);
				//System.out.print(" &" + (srcObj.operand & dstObj.operand));
				
				cc.set(false, //後で書く 
						tmp==0, 
						false, 
						cc.c);

				break;
			case BIS:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));

				if(srcObj.flgRegister){
					if(dstObj.flgRegister){
						//System.out.print(" src=" + reg.get(srcObj.register));
						//System.out.print(" dst=" + reg.get(dstObj.register));
						tmp = reg.get(srcObj.register) | reg.get(dstObj.register);
						reg.set(dstObj.register, reg.get(srcObj.register) | reg.get(dstObj.register));
					}else if(dstObj.flgAddress){
						tmp = reg.get(srcObj.register) | getMemory2(dstObj.address);
						setMemory2(dstObj.address, reg.get(srcObj.register) | getMemory2(dstObj.address));
					}

				}else if(srcObj.flgAddress){
					if(dstObj.flgRegister){
						//System.out.print(" src=" + srcObj.address);
						//System.out.print(" src=" + getMemory2(srcObj.address));
						//System.out.print(" dst=" + reg.get(dstObj.register));

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
				
				cc.set(false, //後で書く 
						tmp==0, 
						false, 
						cc.c);

				break;
			case BISB:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				
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
				
				cc.set(false, //後で書く 
						tmp == 0, 
						false, 
						cc.c);

				break;
			case SXT:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
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
			case ADD:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
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
			case SUB:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));

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
			case NEG:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				tmp = (~(dstObj.operand << 16) >>> 16) + 1;
				
				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else{
					setMemory2(dstObj.address, tmp);
				}				

				cc.set((tmp >> 15)>0, tmp==0, tmp==100000, tmp!=0);

				break;
			case ASL:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
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
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
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
			case SOB:
				short tmpShort = (short)(reg.get(getOctal(opcode,3)) - 1);
				reg.set(getOctal(opcode,3),((reg.get(getOctal(opcode,3)) - 1) << 16) >>> 16);
				if(tmpShort != 0){
					reg.set(7,getOffset6(getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case BR:
				reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				break;
			case BCC:
				if(cc.c == false){
					reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case BNE:
				if(cc.z == false){
					reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case BEQ:
				if(cc.z == true){
					reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case BGE:
				if(cc.n == cc.v){
					reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case BGT:
				if(cc.z == false && cc.n == cc.v){
					reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case BHI:
				if(cc.c == false && cc.z == false){
					reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case BLE:
				if(cc.z == true || cc.n != cc.v){
					reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case BLT:
				if(cc.n != cc.v){
					reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case BLOS:
				if(cc.c == true || cc.z == true){
					reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case BCS:
				if(cc.c == true){
					reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case BVS:
				if(cc.v == true){
					reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case BMI:
				if(cc.n == true){
					reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case BPL:
				if(cc.n == false){
					reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case BIC:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));

				tmp = ~(srcObj.operand) & dstObj.operand;

				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else{
					setMemory2(dstObj.address, tmp);
				}

				//System.out.print(" tmp=" + tmp);
				
				cc.set((tmp << 16 >>> 31) > 0, tmp==0, false, cc.c);

				break;
			case BICB:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3),true);
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5),true);

				tmp = ~(srcObj.operand) & dstObj.operand;

				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else{
					setMemory1(dstObj.address, tmp);
				}

				cc.set(tmp>0xFF, tmp==0, false, cc.c);

				break;
			case SYS:
				
				int val1;
				int val2;

				switch(getDex(getOctal(opcode,4),getOctal(opcode,5))){
				case 0: //systemcall
					//if(dbgFlg) System.out.println("\n indir:");
					int sub = getMem();
					tmp = reg.get(7);
					execute(sub, sub+1, true);

					reg.set(7, tmp);

					break;
				case 1: //exit
					if(dbgFlg>0) System.out.println("\n exit:");
					if(childFlg){
						System.out.println("child-end");
						//実行
						if(exeFlg){
							pva.reg.set(0,pid);
							pva.reg.set(7,parentPc+2);
							pva.execute(0, pva.textSize,false,true);
						}
					}else{
						System.exit(0);
					}
					break;
				case 2: //fork
					if(dbgFlg>0) System.out.println("\n fork:");

					System.out.println(" forkpid-def=" + pid);
					System.out.println(" forkreg0-def=" + reg.get(0));

					//仮想メモリを退避
					parentPc = reg.get(7);
					pva = new VirtualAddressSpace();
					pva = (VirtualAddressSpace) this.clone();
					//pva.fork(reg, fd, cc);
					pva.pid = 123;
					pva.reg.set(0, 16);

					System.out.println(" forkpid-def=" + pid);
					System.out.println(" forkpid-pva=" + pva.pid);
					System.out.println(" forkreg0-def=" + reg.get(0));
					System.out.println(" forkreg0-pva=" + pva.reg.get(0));
					
					VirtualAddressSpace forkAddressSpace = new VirtualAddressSpace();
					forkAddressSpace = (VirtualAddressSpace) this.clone();

					forkAddressSpace.pid = 19673;
					forkAddressSpace.childFlg = true;
					//forkAddressSpace.fork(reg, fd, cc);
					forkAddressSpace.reg.set(0, 256);

					System.out.println(" forkpid-def=" + pid);
					System.out.println(" forkpid-pva=" + pva.pid);
					System.out.println(" forkpid-fork=" + forkAddressSpace.pid);
					System.out.println(" forkreg0-def=" + reg.get(0));
					System.out.println(" forkreg0-pva=" + pva.reg.get(0));
					System.out.println(" forkreg0-fork=" + forkAddressSpace.reg.get(0));

					//実行
					if(exeFlg){
						forkAddressSpace.execute(0, forkAddressSpace.textSize,false,true);
					}
					
					/*
					reg.set(0, 19673);
					reg.set(7, reg.get(7)+2);
					*/
					
					break;
				case 3: //read
					val1 = getMem(); //読み込み位置
					val2 = getMem();  //読み込みサイズ
					
					//デバッグ用
					if(dbgFlg>0) System.out.print("\n read:" + reg.get(0) + "," + val1 + "," + val2);
					
					if(fd.getSize(reg.get(0)) < fd.getOffset(reg.get(0))+1){
						reg.set(0,0);
					}else{
						int i;
						for(i=0;i<val2;i++){
							if(fd.getSize(reg.get(0)) < fd.getOffset(reg.get(0))+1){
								break;
							}
							setMemory1(val1, fd.readByte(reg.get(0)));
							val1++;
						}
						reg.set(0,i);
					}
					
					cc.set(cc.n, cc.z, cc.v, false);
					
					break;
				case 4: //write
					val1 = getMem(); //書き込み元データ位置
					val2 = getMem(); //書き込みサイズ

					if(dbgFlg>0) System.out.print("\n write:" + reg.get(0) + "," + val1 + "," + val2);

					int i = 0;
					if(fd.isFile(reg.get(0))){
			
						BlockFile outFile = (BlockFile) fd.get(reg.get(0)); 
				        File file = new File(outFile.inode.toString());
				        
						//ファイル名設定
						Path fileName = file.toPath();
						
						//ファイル内容取得
						byte[] beforeByte = null;
						try {
							beforeByte = java.nio.file.Files.readAllBytes(fileName);
						} catch (IOException e) {
							e.printStackTrace();
						}

						//System.out.println(" beforebyte=" + beforeByte);
						//System.out.println(" beforelength=" + beforeByte.length);
						
						int writeSize = beforeByte.length;
						if(beforeByte.length < fd.getOffset(reg.get(0)) + val2){
							writeSize =  fd.getOffset(reg.get(0)) + val2;
						}
						byte[] writeByte = new byte[writeSize];

						//System.out.println(" writelength=" + writeByte.length);

						//System.out.println(" getOffset=" + fd.getOffset(reg.get(0)));

						if(beforeByte.length < fd.getOffset(reg.get(0))){
							//System.out.print("debug1\n");
							
							for(i=0;i<beforeByte.length;i++){
								writeByte[i] = beforeByte[i];
								//System.out.print(String.format("%02x ", writeByte[i]));
							}
							for(i=beforeByte.length;i<fd.getOffset(reg.get(0));i++){
								writeByte[i] = 0;
								//System.out.print(String.format("%02x ", writeByte[i]));
							}
							//System.out.print("\n");
							
						}else{
							//System.out.print("debug2\n");
							for(i=0;i<fd.getOffset(reg.get(0));i++){
								writeByte[i] = beforeByte[i];
								//System.out.print(String.format("%02x ", writeByte[i]));
							}
							//System.out.print("\n");
						}

						//System.out.print("debug3\n");
						for(i=fd.getOffset(reg.get(0));i<fd.getOffset(reg.get(0))+val2;i++){
					        writeByte[i] = (byte) (getMemory1(val1) << 24 >>> 24);
							val1++;
							//System.out.print(String.format("%02x ", writeByte[i]));
						}
						//System.out.print("\n");
						
						//System.out.print("debug4\n");
						for(i=fd.getOffset(reg.get(0))+val2;i<beforeByte.length;i++){
					        writeByte[i] = beforeByte[i];
					        //System.out.print(String.format("%02x ", writeByte[i]));
						}
						//System.out.print("\n");
						
						//System.out.println(" writebyte=" + writeByte);
						
						for(i=0;i<writeByte.length;i++){
							//System.out.print(String.format("%02x ", writeByte[i]));
						}
						//System.out.print("\n");
						

				        try {
							java.nio.file.Files.write(fileName, writeByte);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} 
						/*
						try{
					        BufferedOutputStream fis = new BufferedOutputStream(new FileOutputStream(file));
					        
			                fis.write(writeByte);
			                
			                fis.flush();
			                fis.close();
					    }catch(IOException e){
					        System.out.println(e);
					    }
					    */
						
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
					reg.set(0,fd.open(fd.search(), openFile.toPath()));

					break;
				case 6: //close
					//デバッグ用
					if(dbgFlg>0) System.out.print("\n close:" + reg.get(0));
					fd.clear(reg.get(0));
					reg.set(0, 0);
					
					break;
				case 8: //create

					File createFile = getFile(getMem(),"creat");
					//System.out.print(" ,");
					getMem();

					if (createFile.exists()){
						createFile.delete();
					}
					
					try {
						createFile.createNewFile();
					} catch (IOException e) {
						e.printStackTrace();
					}

					//とりあえずエラーが発生しない体
					cc.set(cc.n, cc.z, cc.v, false);

					//ファイルディスクリプタに設定
					reg.set(0,fd.open(fd.search(), createFile.toPath()));
					
					break;
				case 9: //link
					//デバッグ用
					if(dbgFlg>0) System.out.print("\n link:");

					int existingInt = getMem();
					StringBuffer existingStr = new StringBuffer(""); 
					
					while(true){
						if(getMemory1(existingInt)!=0){
							existingStr.append((char)getMemory1(existingInt));
							existingInt = existingInt + 1;
						}else{
							break;
						}
					}

					int newInt = getMem();
					StringBuffer newStr = new StringBuffer(""); 
					
					while(true){
						if(getMemory1(newInt)!=0){
							newStr.append((char)getMemory1(newInt));
							newInt = newInt + 1;
						}else{
							break;
						}
					}
					
					//ファイル名設定
					File existringFile = new File(existingStr.toString());
					Path existingLink = existringFile.toPath();

					File newFile = new File(newStr.toString());
					Path newLink = newFile.toPath();
					
					try {
						java.nio.file.Files.createLink(newLink, existingLink);
					} catch (IOException x) {
					    System.err.println(x);
					} catch (UnsupportedOperationException x) {
					    //一部のファイル・システムではディレクトリに対して既存のファイルを追加する操作はサポートされません。
					    System.err.println(x);
					}

					if(dbgFlg>0) System.out.print(":" + existingStr.toString() + "," + newStr.toString());
					
					reg.set(0, 0);
					
					//とりあえずエラーが発生しない体
					cc.set(cc.n, cc.z, cc.v, false);
					
					break;
				case 10: //unlink
					//デバッグ用
					if(dbgFlg>0) System.out.print("\n unlink:");

					int unlinkTmp = getMem();
					StringBuffer unlinkstr = new StringBuffer(""); 
					
					while(true){
						if(getMemory1(unlinkTmp)!=0){
							unlinkstr.append((char)getMemory1(unlinkTmp));
							unlinkTmp = unlinkTmp + 1;
						}else{
							break;
						}
					}

					if(dbgFlg>0) System.out.print(":" + unlinkstr);
					
					reg.set(0, 0);
					
					//とりあえずエラーが発生しない体
					cc.set(cc.n, cc.z, cc.v, false);
					
					break;
				case 11: //exec
					//デバッグ用
					if(dbgFlg>0) System.out.print("\n exec:");

					String execTmp1 =getFileName(getMem());
					if(dbgFlg>0) System.out.print(" ");
					int argsIndex = getMem();
					ArrayList<String> execArgs = new ArrayList<String>();
					
					while(true){
						if(getMemory2(argsIndex) == 0){
							break;
						}
						execArgs.add(getFileName(getMemory2(argsIndex)));
						argsIndex += 2;
					}
					
					//ファイル名設定
					File file = new File(execTmp1);
					Path fileName = file.toPath();
						
					//引数設定
					int argSize = 0;
					ArrayList<byte[]> arg = new ArrayList<byte[]>();
					Stack<Byte> argStack = new Stack<Byte>();

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

					//ファイル内容取得
					byte[] bf = null;
					try {
				        bf = java.nio.file.Files.readAllBytes(fileName);
					} catch (IOException e) {
						e.printStackTrace();
					}

					//仮想メモリにロード
					VirtualAddressSpace vas = new VirtualAddressSpace(bf);

					//実行前設定
					vas.reset(dbgFlg, mmrFlg, argStack);

					//実行
					if(exeFlg){
						vas.execute(0, vas.textSize);
					}

					//とりあえずエラーにしておく仕様
					cc.set(cc.n, cc.z, cc.v, false);

					break;
				case 15: //chmod
					//デバッグ用
					if(dbgFlg>0) System.out.print("\n chmod:");
					int chmodIndex = getMem();
					if(dbgFlg>0) System.out.print(" " + getFileName(chmodIndex) + " ");
					int chmodIndex2 = getMem();
					if(dbgFlg>0) System.out.print(" " + chmodIndex2);
					reg.set(0, 0);
					
					break;
				case 17: //brk
					//デバッグ用
					if(dbgFlg>0) System.out.print("\n brk:");
					reg.set(0, 0);
					
					break;
				case 18: //stat
					File statFile = getFile(getMem(),"stat");
					
					//デバッグ用
					if(dbgFlg>0) System.out.print(" ,");

					getMem();
					
					if(statFile.isFile()){
						
					}else{
						cc.set(cc.n, cc.z, cc.v, true);
					}
					
					break;
				case 19: //lseek
					val1 = getMem(); //シークサイズ
					val2 = getMem(); //モード
					
					//デバッグ用
					if(dbgFlg>0) System.out.print("\n lseek:" + reg.get(0) + "," + val1 + "," + val2);
					
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
					//デバッグ用
					if(dbgFlg>0) System.out.print("\n getpid:");
					/*
				    String processName =
				    	      java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
				    int pid = (int)Long.parseLong(processName.split("@")[0]);
				    System.out.println(" " + pid);
				    */
					
					if(dbgFlg>0) System.out.println(" " + pid);
					reg.set(0,pid);

					break;
				case 41: //dup
					//デバッグ用
					if(dbgFlg>0) System.out.print("\n dup:" + reg.get(0));
					
					reg.set(0,fd.copy(fd.search(), reg.get(0)));

					break;
				case 48: //signal
					val1 = getMem(); //シークサイズ
					val2 = getMem(); //モード

					//デバッグ用
					if(dbgFlg>0) System.out.print("\n signal:" + reg.get(0) + "," + val1 + "," + val2);

					signal.set(val1, val2);
					reg.set(0,0);

					break;
				}
				if(dbgFlg==1){
					System.out.print("\n");
				}

				break;
			case DIV: 
				int divR1 = reg.get(getOctal(opcode,3)) << 16;
				int divR2 = reg.get(getOctal(opcode,3)+1);
				
				int divValue = divR1 + divR2;
				
				srcObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				
				reg.set(getOctal(opcode,3), divValue / srcObj.operand);
				reg.set(getOctal(opcode,3)+1, divValue % srcObj.operand);

				cc.set((reg.get(getOctal(opcode,3)) >> 15)>0, 
						reg.get(getOctal(opcode,3))==0, 
						srcObj.operand==0, //後で書く
						srcObj.operand==0);
				
				break;
			case MUL: //後で書く
				int mulR = reg.get(getOctal(opcode,3));
				srcObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				
				if(getOctal(opcode,3)%2 ==0){
					reg.set(getOctal(opcode,3), (mulR * srcObj.operand >> 16) << 16);
					reg.set(getOctal(opcode,3)+1, (mulR * srcObj.operand << 16) >>> 16);
				}else{
					reg.set(getOctal(opcode,3), (mulR * srcObj.operand << 16) >>> 16);
				}
				cc.set((mulR * srcObj.operand  >>> 15)>0, 
						mulR * srcObj.operand==0, 
						false,
						false);
				break;
			case ASH: 
				int ashReg = reg.get(getOctal(opcode,3));
				srcObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				int ashInt = srcObj.operand << 26;
				ashInt = ashInt >> 26;
				if(ashInt < 0){
					ashReg = ashReg << 16;
					ashReg = ashReg >> 16;
					reg.set(getOctal(opcode,3), ashReg >> Math.abs(ashInt));
					reg.set(getOctal(opcode,3), (reg.get(getOctal(opcode,3)) << 16) >>> 16);
				}else{
					reg.set(getOctal(opcode,3), ashReg << ashInt);
					reg.set(getOctal(opcode,3), (reg.get(getOctal(opcode,3)) << 16) >>> 16);
				}
				
				cc.set((reg.get(getOctal(opcode,3)) << 1 >>> 16)>0,  //要調査
						reg.get(getOctal(opcode,3))==0, 
						((ashReg << 16 ) >>> 31) != ((reg.get(getOctal(opcode,3)) << 16) >>> 31), //要調査
						false); //後で書く
				
				break;
			case ASHC: //後で書く
				int ashcReg1 = reg.get(getOctal(opcode,3));
				int ashcReg2 = reg.get(getOctal(opcode,3) + 1);
				int ashcTmp = (ashcReg1 << 16) + (ashcReg2 << 16 >>> 16);
				
				srcObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				int ashcInt = srcObj.operand << 26 >> 26;
			
				if(ashcInt < 0){
					tmp = ashcTmp >> Math.abs(ashcInt);
					reg.set(getOctal(opcode,3), ashcTmp >> Math.abs(ashcInt) >>> 16);
					reg.set(getOctal(opcode,3)+1, ashcTmp >> Math.abs(ashcInt) << 16 >>> 16);
				}else{
					tmp = ashcTmp << Math.abs(ashcInt);
					reg.set(getOctal(opcode,3), ashcTmp << Math.abs(ashcInt) >>> 16);
					reg.set(getOctal(opcode,3)+1, ashcTmp << Math.abs(ashcInt) << 16 >>> 16);
				}
				
				cc.set(tmp>0,  //要調査
						tmp==0, 
						(ashcTmp >>> 31) != (tmp  >>> 31), //要調査
						false); //後で書く
				
				break;
			case SETD:
				break;
			case WORD:
				break;
			}
		}
	}
	
	//ファイル返却
	File getFile(int val,String debugName){

		//デバッグ用
		if(dbgFlg>0) System.out.print("\n " + debugName);
		
		//ファイル名設定
		File file = new File(getFileName(val));
		
		return file;
		
	}
	
	//FileName返却
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
		/*
		
		if(str.charAt(0) == '/' && str.charAt(1) == 't' && str.charAt(2) == 'm' && str.charAt(3) == 'p' && str.charAt(4) == '/'){
			str2.append("D:\\03.workspace\\v6tmp\\");
			str2.append(str.substring(5));
		}else if(str.charAt(0) == '/' && str.charAt(1) == 'b' && str.charAt(2) == 'i' && str.charAt(3) == 'n' && str.charAt(4) == '/'){
			str2.append("D:\\03.workspace\\v6root\\bin\\");
			str2.append(str.substring(5));
		}else if(str.charAt(0) == '/' && str.charAt(1) == 'l' && str.charAt(2) == 'i' && str.charAt(3) == 'b' && str.charAt(4) == '/'){
			str2.append("D:\\03.workspace\\v6root\\lib\\");
			str2.append(str.substring(5));
		}else{
			str2.append(str.substring(0));
		}
		*/

		if(str.charAt(0) == '/' && str.charAt(1) != 'h'){
			str.insert(0, "/home/zer0/v6root");
		}
		str2.append(str.substring(0));

		//デバッグ用
		if(dbgFlg>0) System.out.print(" :" + str2.toString());
		
		return str2.toString();
	}	

	//加算オーバーフロー判定
	boolean getAddOverflow(int src, int dst, int val){
		boolean addV = false;
		if((dst << 1 >>> 16) == (src << 1 >>> 16)){
			if((dst << 1 >>> 16) != (val << 1 >>> 16)){
				addV = true;
			}
		}
		return addV;
	}

	//減算オーバーフロー判定
	boolean getSubOverflow(int src, int dst, int val){
		boolean subV = false;
		if((dst << 16 >>> 31) != (src << 16 >>> 31)){
			if((dst << 16 >>> 31) == (val << 16 >>> 31)){
				subV = true;
			}
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
				if(((val << 16) >>> 31) == 0){
					addC = true;
				}
			}
		}else{
			if(((dst << 16) >>> 31) == 1){
				if(((val << 16) >>> 31) == 0){
					addC = true;
				}
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
				if(((val << 16) >>> 31) == 1){
					subC = true;
				}
			}
		}else{
			if(((dst << 16) >>> 31) == 1){
				if(((val << 16) >>> 31) == 1){
					subC = true;
				}
			}
		}
		return subC;
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

	//フィールド取得（PC+オフセット*2 8bit（符号付））
	FieldDto getOffset(int first,int second,int third){
		FieldDto operand = new FieldDto();
		int tmp = (first << 6) + (second << 3) + third;
		byte tmpByte = (byte)tmp;
		tmp = reg.get(7) + tmpByte * 2;
		
		operand.setStr("0x" + String.format("%x",tmp));
		operand.setAddress(tmp);

		return operand;
	}

	//フィールド取得（PC-オフセット*2 6bit（符号なし、正の数値））
	FieldDto getOffset6(int first,int second){
		FieldDto operand = new FieldDto();
		int tmp = (first << 3) + second;
		tmp = reg.get(7) - tmp * 2;

		operand.setStr("0x" + String.format("%x",tmp));
		operand.setAddress(tmp);

		return operand;
	}

	//フィールド取得（8進数 6bit）
	FieldDto getNormal(int first,int second,int third){
		FieldDto operand = new FieldDto();
		operand.setStr(String.format("%o",(first << 6) + (second << 3) + third));
		operand.setAddress(((first << 3) + second) * 2 + reg.get(7));

		return operand;
	}

	//フィールド取得（dst,src）
	FieldDto getField(int mode, int regNo){
		FieldDto field = getField(mode, regNo, false);
		return field;
	}

	//フィールド取得（dst,src）
	FieldDto getField(int mode, int regNo, boolean byteFlg){

		//返り値
		FieldDto field = new FieldDto();

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
				field.setStr(getRegisterName(regNo));
				if(exeFlg){
					//System.out.print(" reg=" + reg.get(regNo));
					field.setOperand(reg.get(regNo));
					field.setReg(regNo);
				}
				break;
			case 1:
				//レジスタ間接
				//registerにオペランドのアドレスがある。
				field.setStr("(" + getRegisterName(regNo) + ")");
				if(exeFlg){
					if(byteFlg){
						field.setOperand(getMemory1(reg.get(regNo)));
						field.setAddress(reg.get(regNo));
					}else{
						field.setOperand(getMemory2(reg.get(regNo)));
						field.setAddress(reg.get(regNo));
					}
				}
				break;
			case 2:
				//自動インクリメント
				//registerにオペランドのアドレスがあり、命令実行後にregisterの内容をインクリメントする。
				field.setStr("(" + getRegisterName(regNo) + ")+");
				if(exeFlg){
					if(byteFlg){
						
						//System.out.print(" autoincliment ");
						//System.out.print(" regNo=" + regNo);
						//System.out.print(" regAddress=" + reg.get(regNo));

						//System.out.print(" getmemory=" + getMemory1(reg.get(regNo)));
						//System.out.print(" address=" + reg.get(regNo));

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
				}
				break;
			case 3:
				//自動インクリメント間接
				//registerにオペランドへのポインタのアドレスがあり、命令実行後にregisterの内容を2だけインクリメントする。
				field.setStr("*(" + getRegisterName(regNo) + ")+");
				if(exeFlg){
					field.setOperand(getMemory2(getMemory2(reg.get(regNo))));
					field.setAddress(getMemory2(reg.get(regNo)));
					//reg.add(regNo,4);
					reg.add(regNo,2);
				}
				break;
			case 4:
				//自動デクリメント
				//命令実行前にregisterをデクリメントし、それをオペランドのアドレスとして使用する。
				if(exeFlg){
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
				}
				field.setStr("-(" + getRegisterName(regNo) + ")");
				break;
			case 5:
				//自動デクリメント間接
				//命令実行前にregisterを2だけデクリメントし、それをオペランドへのポインタのアドレスとして使用する。
				if(exeFlg){
					//reg.add(regNo,-4);
					reg.add(regNo,-2);
					field.setOperand(getMemory2(getMemory2(reg.get(regNo))));
					field.setAddress(getMemory2(reg.get(regNo)));
				}
				field.setStr("*-(" + getRegisterName(regNo) + ")");
				break;
			case 6:
				//インデックス
				//register+Xがオペランドのアドレス。Xはこの命令に続くワード。
				opcodeShort = (short)getMem();
				if(opcodeShort < 0){
					field.setStr("-" + String.format("%o",~(opcodeShort - 1)) + "(" + getRegisterName(regNo) + ")");
				}else{
					field.setStr(String.format("%o",opcodeShort) + "(" + getRegisterName(regNo) + ")");
				}
				if(exeFlg){
					//System.out.print(" getMemory2=" + getMemory2(reg.get(regNo)));
					//System.out.print(" opcodeShort=" + opcodeShort);

					if(byteFlg){
					
						//int exeAddress = reg.get(regNo) + opcodeShort;

						//System.out.print(" execaddress=" + exeAddress);
					
						//if(exeAddress > 0xFFFF) exeAddress = exeAddress - 0xFFFF;

						//System.out.print(" execaddress=" + exeAddress);

						field.setOperand(getMemory1(reg.get(regNo) + opcodeShort));
						field.setAddress(reg.get(regNo) + opcodeShort);
					}else{
						field.setOperand(getMemory2(reg.get(regNo) + opcodeShort));
						field.setAddress(reg.get(regNo) + opcodeShort);
						
					}
					
					//System.out.print(" break");

				}
				break;
			case 7:
				//インデックス間接
				//register+Xがオペランドへのポインタのアドレス。Xはこの命令に続くワード。
				opcodeShort = (short)getMem();
				if(opcodeShort < 0){
					field.setStr("*-" + String.format("%o",~(opcodeShort - 1)) + "(" + getRegisterName(regNo) + ")");
				}else{
					field.setStr("*-" + String.format("%o",opcodeShort) + "(" + getRegisterName(regNo) + ")");
				}
				if(exeFlg){
					field.setOperand(getMemory2(getMemory2(reg.get(regNo) + opcodeShort)));
					field.setAddress(getMemory2(reg.get(regNo) + opcodeShort));
				}
				break;
			}
			break;

		case 7:
			switch(mode){
			case 0:
				//レジスタ
				//registerにオペランドがある。
				field.setStr(getRegisterName(regNo));
				if(exeFlg){
					field.setOperand(reg.get(regNo));
					field.setReg(regNo);
				}
				break;
			case 1:
				//レジスタ間接
				//registerにオペランドのアドレスがある。
				field.setStr("(" + getRegisterName(regNo) + ")");
				if(exeFlg){
					if(byteFlg){
						field.setOperand(getMemory1(reg.get(regNo)));
						field.setAddress(reg.get(regNo));
					}else{
						field.setOperand(getMemory2(reg.get(regNo)));
						field.setAddress(reg.get(regNo));
					}
				}
				break;			
			case 2:
				//イミディエート
				//オペランドは命令内にある。
				opcodeShort = (short)getMem();
				if(opcodeShort < 0){
					field.setStr("$" + "-" + String.format("%o",~(opcodeShort - 1)));
				}else{
					field.setStr("$" + String.format("%o",opcodeShort));
				}
				if(exeFlg){
					field.setOperand((int)opcodeShort);
				}
				break;
			case 3:
				//絶対
				//オペランドの絶対アドレスが命令内にある。
				opcodeShort = (short)getMem();
				if(opcodeShort < 0){
					field.setStr("*$" + "-" + String.format("%o",~(opcodeShort - 1)));
				}else{
					field.setStr("*$" + String.format("%o",opcodeShort));
				}
				if(exeFlg){
					field.setOperand((int)opcodeShort); //未検証
					field.setAddress((int)opcodeShort);
				}
				break;
			case 4:
				//自動デクリメント
				//命令実行前にregisterをデクリメントし、それをオペランドのアドレスとして使用する。
				if(exeFlg){
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
				}
				field.setStr("-(" + getRegisterName(regNo) + ")");
				break;
			case 5:
				//自動デクリメント間接
				//命令実行前にregisterを2だけデクリメントし、それをオペランドへのポインタのアドレスとして使用する。
				if(exeFlg){
					reg.add(regNo,-4);
					field.setOperand(getMemory2(getMemory2(reg.get(regNo))));
					field.setAddress(getMemory2(reg.get(regNo)));
				}
				field.setStr("*-(" + getRegisterName(regNo) + ")");
				break;
			case 6:
				//相対
				//命令に続くワードの内容 a を PC+2 に加算したものをアドレスとして使用する。
				opcodeShort = (short)getMem();
				tmp = opcodeShort + reg.get(7);
				
				field.setStr("0x" + String.format("%02x",tmp));
				if(exeFlg){
					field.setOperand(getMemory2(tmp));
					field.setAddress(tmp);
				}
				break;
			case 7:
				//相対間接
				//命令に続くワードの内容 a を PC+2 に加算したものをアドレスのアドレスとして使用する。
				opcodeInt = (int)getMem() << 16 >>> 16;
				
				tmp = opcodeInt + reg.get(7);

				field.setStr("*$0x" + String.format("%02x",(tmp)));
				field.setOperand(getMemory2(tmp)); //未検証
				field.setAddress(getMemory2(tmp));
				break;
			}
			break;
		}	
		return field;
	}

	//ニーモニック取得
	Mnemonic getMnemonic(int opcode){
		Mnemonic mnemonic = null;

		switch(getOctal(opcode,0)){
		case 0:
			switch(getOctal(opcode,1)){
			case 0:
				switch(getOctal(opcode,2)){
				case 0:
					switch(getOctal(opcode,3)){
					case 0:
						switch(getOctal(opcode,4)){
						case 0:
							switch(getOctal(opcode,5)){
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
						switch(getOctal(opcode,4)){
						case 0:
							mnemonic = Mnemonic.RTS;
							break;
						case 6:
							switch(getOctal(opcode,5)){
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
					switch(getOctal(opcode,3)){
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
					switch(getOctal(opcode,3)){
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
					switch(getOctal(opcode,3)){
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
					switch(getOctal(opcode,3)){
					case 0:
						mnemonic = Mnemonic.CLR;
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
					switch(getOctal(opcode,3)){
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
				switch(getOctal(opcode,2)){
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
				case 7:
					mnemonic = Mnemonic.SOB;
					break;
				}
				break;
			}
			break;
		case 1:
			switch(getOctal(opcode,1)){
			case 0:
				switch(getOctal(opcode,2)){
				case 0:
					switch(getOctal(opcode,3)){
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
					switch(getOctal(opcode,3)){
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
					switch(getOctal(opcode,3)){
					case 4:
					case 5:
					case 6:
					case 7:
						mnemonic = Mnemonic.BVS;
						break;
					}
					break;
				case 3:
					switch(getOctal(opcode,3)){
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
					switch(getOctal(opcode,3)){
					case 4:	
						mnemonic = Mnemonic.SYS;
						break;
					}
					break;
				case 5:
					switch(getOctal(opcode,3)){
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

		System.out.print(" " + String.format(" 0xffe2=%04x",getMemory2(0xffe2)));
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
			if(m%16==0){
				System.out.print(String.format("\n%02x:",m/16));
			}
			System.out.print(" " + String.format("%04x",getMemory2(m)));
		}
		/*
		for(int m=0;m<textSize+dataSize+bssSize;m=m+2){
			if(m%16==0){
				System.out.print(String.format("\n%02x:",m/16));
			}
			System.out.print(" " + String.format("%04x",getMemory2(m)));
		}
		System.out.print("\n--memory-center-------------");
		for(int m=memorySize-128;m<memorySize;m=m+2){
			if(m%16==0){
				System.out.print("\n");
			}
			System.out.print(" " + String.format("%04x",getMemory2(m)));
		}
		*/
		System.out.println("\n--memory-end-------------");
	}
}

/*
 * ニーモニックENUM
 */
enum Mnemonic { 
	RTT, RTS, JMP, JSR, CLR, CLRB, TST, TSTB, MOV, MOVB, CMP, CMPB, BIT, BITB, BISB, BIS, ADC,
	INC, DEC, SUB, ADD, SOB, SXT, INCB, ROR, DECB, SWAB, ROL,
	BR, BHI, BNE, BEQ, BCC, BGT, BGE, BIC, BLE, BLOS, BCS, BLT, BICB, BVS, BMI, BPL,
	NEG, ASL, ASR,
	DIV, ASH, ASHC, MUL,
	SETD, SYS, WORD,
	SEV
};

/*
 * フィールドDto
 */
class FieldDto{

	String str;
	int operand;
	short operandShort;
	int address;
	int register;

	boolean flgRegister;
	boolean flgAddress;

	public FieldDto(){
		flgRegister = false;
		flgAddress = false;
	}

	public void setOperand(int input){
		operand = input;
		operandShort = (short)input;
	}

	public void setStr(String input){
		str = input;
	}

	public void setAddress(int input){
		address = input;
		flgAddress = true;
	}

	public void setReg(int input){
		register = input;
		flgRegister = true;
	}

	public void set(FieldDto input){
		operand = input.operand;
		str = input.str;
		address = input.address;
		register = input.register;
		flgAddress = input.flgAddress;
		flgRegister = input.flgRegister;
	}
}

/*
 * シグナル
 */
class Signal{
	
	//シグナル
	int[] signal;
	
	//コンストラクタ（初期化）
	Signal(){
		signal = new int[14];
		reset();
	}
	
	void reset(){
		for(int i=0;i<signal.length;i++){
			signal[i] = 0;
		}
	}
	
	void set(int num, int pointer){
		signal[num] = pointer;
	}
	
}

/*
 * レジスタクラス
 * R1-R5:汎用レジスタ
 * R6:スタックポインタSP
 * R7:プログラムカウンタPC
 */
class Register implements Cloneable{

	//レジスタ
	int[] reg;

	public Object clone() {
    	int[] reg2 = new int[reg.length];
    	reg2 = reg.clone();
	    return new Register(reg2);  
	}
	
	Register(int[] befReg){
		reg = befReg;
	}
	
	
	//コンストラクタ（初期化）
	Register(){
		reg = new int[8];
		reset();
	}

	//レジスタ初期化
	void reset(){
		reg[0] = 0;
		reg[1] = 0;
		reg[2] = 0;
		reg[3] = 0;
		reg[4] = 0;
		reg[5] = 0;
		reg[6] = 65536; //spは最後尾のアドレスを指す
		reg[7] = 0;
	}

	//レジスタを上書き
	void set(int regNo,int val){
		reg[regNo] = val;
	}

	//レジスタに加算
	void add(int regNo,int val){
		if(reg[regNo]+val > 0xffff){
			reg[regNo] = (reg[regNo]+val) << 16 >>> 16;
		}else{
			reg[regNo] = reg[regNo] + val;
		}
	}

	//レジスタを取得
	int get(int regNo){
		return reg[regNo];
	}
}

/*
 * ファイルディスクリプタクラス
 */
class FileDescriptor{
	BlockFile inode[];
	
	//コンストラクタ（初期化）
	FileDescriptor(){
		inode = new BlockFile[16];
		inode[0] = new BlockFile(System.in,true,false,false,false);
		inode[1] = new BlockFile(System.out,false,true,false,false);
		inode[2] = new BlockFile(System.err,false,false,true,false);
	}
	
	//open
	int open(int no,Object in){
		inode[no] = new BlockFile(in,false,false,false,true);
		inode[no].open();
		return no;
	}
	
	//種類を取得
	boolean isStdin(int no){
		return inode[no].stdin;
	}
	boolean isStdout(int no){
		return inode[no].stdout;
	}
	boolean isStderror(int no){
		return inode[no].stderror;
	}
	boolean isFile(int no){
		return inode[no].file;
	}
	
	//get
	Object get(int no){
		return inode[no];
	}
	
	//readByte
	byte readByte(int no){
		return inode[no].readByte();
	}
	
	//getSize
	int getSize(int no){
		return inode[no].getSize();
	}

	//getOffset
	int getOffset(int no){
		return inode[no].getOffset();
	}

	//setOffset
	int setOffset(int no,int offset){
		inode[no].setOffset(offset);
		return inode[no].getOffset();
	}
	
	//clear
	int clear(int no){
		inode[no] = null;
		return no;
	}
	
	//copy
	int copy(int to,int from){
		inode[to] = inode[from];
		return to;
	}
	
	//search
	int search(){
		for(int i=0;i<16;i++){
			if(inode[i]==null){
				return i;
			}
		}
		return 16;
	}
	
}

/*
 * ファイルクラス
 */
class BlockFile{
	Object inode;
	int offset;
	byte[] strage;
	boolean stdin;
	boolean stdout;
	boolean stderror;
	boolean file;
	
	//コンストラクタ（初期化）
	BlockFile(Object input,boolean in,boolean out,boolean error,boolean fl){
		inode = input;
		offset = 0;
		strage = null;
		stdin = in;
		stdout = out;
		stderror = error;
		file = fl;
	}
	
	void open(){
		try {
			strage = java.nio.file.Files.readAllBytes((Path)inode);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	
	int getSize(){
		return strage.length;
	}

	int getOffset(){
		return offset;
	}

	int setOffset(int in){
		offset = in;
		return offset;
	}
	
	byte readByte(){
		byte val = strage[offset];
		offset++;
		return val;
	}

}

/*
 * コンディションコードクラス
 * Z:ゼロの場合
 * N:負の場合
 * C:MSB(最上位ビット)からキャリが発生、MSB/LSB(最下位ビット)から1がシフトされた場合
 * V:オーバーフローが発生した場合
 */
class ConditionCode{

	boolean n;
	boolean z;
	boolean v;
	boolean c;

	//コンストラクタ（初期化）
	ConditionCode(){
		reset();
	}

	//コンディションコード初期化
	void reset(){
		n = false;
		z = false;
		v = false;
		c = false;
	}
	
	//コンディションコード設定
	void set(boolean boolN,boolean boolZ,boolean boolV,boolean boolC){
		n= boolN;
		z= boolZ;
		v= boolV;
		c= boolC;
	}
}