package pdp11;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class Pdp11{

	public static void main(String[] args){
		//オプション
		boolean flgDism = false;
		boolean flgDebug = false;
		boolean flgExe = false;
		boolean flgMmr = false;

		//引数エラー
		if (args.length>1){
			for(int i=1;i<args.length;i++){
				if(!(args[i].substring(0,1).equals("-"))){
					System.out.println("オプションを設定する場合は、ファイル名 -オプションと入力してください");
					System.exit(0);
				}
				if(args[i].equals("-v")){
					flgDebug = true;
				}
				if(args[i].equals("-d")){
					flgDism = true;
				}
				if(args[i].equals("-e")){
					flgExe = true;
				}
				if(args[i].equals("-m")){
					flgMmr = true;
				}

			}
		}else if (args.length!=1){
			System.out.println("ファイル名は一つだけ指定してください");
			System.exit(0);
		}else{
			flgDism = true;
			flgExe = true;
		}

		//ファイル名取得
		File file = new File(args[0]);
		Path fileName = file.toPath();

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

		//デバッグ、ダンプ設定
		if(flgDebug){
			if(flgMmr){
				vas.reset(1,1);
			}else{
				vas.reset(1,0);
			}
		}else{
			if(flgMmr){
				vas.reset(0,1);
			}else{
				vas.reset(0,0);
			}
		}

		//実行
		if(flgExe){
			vas.execute(0, vas.textSize);
		}

	}
}

/*
 * レジスタクラス
 */
class Register{

	//レジスタ
	int[] reg;

	//コンストラクタ（初期化）
	Register(){
		reg = new int[8];
		reset();
	}

	void reset(){
		reg[0] = 0;
		reg[1] = 0;
		reg[2] = 0;
		reg[3] = 0;
		reg[4] = 0;
		reg[5] = 0;
		reg[6] = 65526; //spは最後尾のアドレスを指す
		reg[7] = 0;
	}

	void set(int regNo,int val){
		reg[regNo] = val;
	}

	void add(int regNo,int val){
		reg[regNo] = reg[regNo] + val;
	}

	int get(int regNo){
		return reg[regNo];
	}

}

/*
 * コンディションコードクラス
 */
class ConditionCode{

	int n;
	int z;
	int v;
	int c;

	//コンストラクタ（初期化）
	ConditionCode(){
		reset();
	}

	void reset(){
		n = 0;
		z = 0;
		v = 0;
		c = 0;
	}

	void setN(int input){
		if(input < 0){
			n = 1;
		}else{
			n = 0;
		}
	}

	void setZ(int input){
		if(input == 0){
			z = 1;
		}else{
			z = 0;
		}
	}

	void setV(int input){
		if(input >= 0x10000){
			v = 1;
		}else{
			v = 0;
		}
	}

	void clearV(){
		v = 0;
	}

	void setC(int input){
		if(input < Math.pow(2.0, 16.0)){
			c = 1;
		}else{
			c = 0;
		}
	}

	void clearC(){
		c = 0;
	}
}

/*
 * 仮想アドレス空間クラス
 */
class VirtualAddressSpace{

	//仮想メモリ
	byte[] mem;
	//メモリサイズ
	int memorySize = 65536;

	//レジスタ
	Register reg;

	//コンディションコード
	ConditionCode cc;

	//出力用ユーティリティ
	int strnum;

	//モード（逆アセンブル=0,実行=1）
	int exeFlg;

	//モード（実行=0,デバッグ=1）
	int dbgFlg;
	int mmrFlg;

	//領域の大きさ
	int headerSize = 16;
	int textSize;
	int dataSize;
	int bssSize;
	
	//マジックナンバー
	int magicNo;

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

		//コンディションコード初期化
		cc = new ConditionCode();
	}

	//2バイト単位でリトルエンディアンを反転して10進数で取得
	int getMemory2(int start){
		return (int) ((int)(mem[start]) & 0xFF)|( (int)((mem[start+1] & 0xFF) << 8));
	}

	//8進数に変換した命令の任意の箇所を取得
	int getOctal(int dec,int index){
		int val = Integer.parseInt(String.format("%06o",dec).substring(index, index+1));
		return val;
	}

	//ASCIIコードに変換したデータの任意の箇所を取得
	char getChar(int dec,int index){
		char val = (char)Integer.parseInt(String.format("%02x",dec),16);
		System.out.print(val);
		return val;
	}

	//メモリ上のデータを取得して、PC+2する
	int getMem(){
		int opcode = getMemory2(reg.get(7));

		//逆アセンブルの場合は出力
		if(exeFlg == 0){
			printOpcode(opcode);
			strnum++;
		}else{
			if(dbgFlg == 1){
				printOpcode(opcode);
			}
		}

		//PC+2
		reg.add(7,2);

		return opcode;
	}

	//指定箇所のメモリを1byte取得
	int getMemory1(int start){
		return mem[start];
	}

	//指定箇所のメモリを更新
	void setMemory2(int add,int src){
		mem[add] = (byte)src;
		mem[add+1] = (byte)(src >> 8);
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

		//実行フラグ=0
		exeFlg = 0;

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
			case INC:
				mnemonic = "inc";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case DEC:
				mnemonic = "dec";
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
			case CMP:
				mnemonic = "cmp";
				srcOperand = getField(getOctal(opcode,2),getOctal(opcode,3)).str;
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BCC:
				mnemonic = "bcc";
				srcOperand = getPcAddOff2(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case BNE:
				mnemonic = "bne";
				srcOperand = getPcAddOff2(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case BEQ:
				mnemonic = "beq";
				srcOperand = getPcAddOff2(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case BGT:
				mnemonic = "bgt";
				srcOperand = getPcAddOff2(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case BHI:
				mnemonic = "bhi";
				srcOperand = getPcAddOff2(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case SYS:	
				mnemonic = "sys";
				srcOperand = String.valueOf(getOctal(opcode,5));
				dstOperand = "";
				break;
			case SUB:
				mnemonic = "sub";
				srcOperand = getField(getOctal(opcode,2),getOctal(opcode,3)).str;
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
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
	public void reset(int debugFlg,int memoryFlg){
		//デバッグフラグ
		dbgFlg = debugFlg;
		mmrFlg = memoryFlg;

		//レジスタ初期化
		reg.reset();

		//コンディションコード初期化
		cc.reset();
	}

	//インタプリタ
	public void execute(int start, int end){

		//実行フラグ=1
		exeFlg = 1;

		reg.set(7,start);	

		for(reg.set(7,start);reg.get(7)<end;){

			//debug用
			if(dbgFlg == 1){
				printDebug();
			}
			
			FieldDto srcObj;
			FieldDto dstObj;

			int tmp = 0;

			//命令取得
			int opcode = getMem();
			Mnemonic nic = getMnemonic(opcode);

			switch(nic){
			case RTT:
				break;
			case JMP:
				FieldDto a = getField(getOctal(opcode,4),getOctal(opcode,5));

				if(a.flgRegister){
					reg.set(7,reg.get(a.register));
				}else if(a.flgAddress){
					reg.set(7,a.address);
				}else{
					reg.set(7,a.operand);
				}
				break;
			case RTS:
				reg.set(7,reg.get(getOctal(opcode,5)));
				reg.set(getOctal(opcode,5),getMemory2(reg.get(6)));
				reg.add(6,2);
				break;
			case JSR:
				srcObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				pushStack(reg.get(getOctal(opcode,3)));
				reg.set(getOctal(opcode,3),reg.get(7));
				reg.set(7, srcObj.address);
				break;
			case CLR:
				setMemory2(getField(getOctal(opcode,4),getOctal(opcode,5)).operand,0);
				cc.reset();
				break;
			case INC:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				if(dstObj.flgRegister){
					tmp = reg.get(dstObj.register) + 1;
					cc.setN(tmp);
					cc.setZ(tmp);
					reg.set(dstObj.register, tmp);
				}else if(dstObj.flgAddress){
					tmp = getMemory2(dstObj.address) + 1;
					cc.setN(tmp);
					cc.setZ(tmp);
					setMemory2(dstObj.address, tmp);
				}else{
					tmp = dstObj.operand + 1;
					cc.setN(tmp);
					cc.setZ(tmp);
					setMemory2(dstObj.address, tmp);
				}
				break;
			case DEC:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				if(dstObj.flgRegister){
					tmp = reg.get(dstObj.register) - 1;
					cc.setN(tmp);
					cc.setZ(tmp);
					reg.set(dstObj.register, tmp);
				}else if(dstObj.flgAddress){
					tmp = getMemory2(dstObj.address) - 1;
					cc.setN(tmp);
					cc.setZ(tmp);
					setMemory2(dstObj.address, tmp);
				}else{
					tmp = dstObj.operand - 1;
					cc.setN(tmp);
					cc.setZ(tmp);
					setMemory2(dstObj.address, tmp);
				}
				break;
			case TST:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				cc.setN(dstObj.operand);
				cc.setZ(dstObj.operand);
				cc.clearV();
				cc.clearC();

				break;
			case MOVB:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3),1);
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5),1);

				cc.setN(srcObj.operand);
				cc.setZ(srcObj.operand);
				cc.clearV();

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
				break;
			case MOV:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));

				cc.setN(srcObj.operand);
				cc.setZ(srcObj.operand);
				cc.clearV();

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
				break;
			case CMP:	//後で確認
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				tmp = srcObj.operand -dstObj.operand;

				cc.setN(tmp);
				cc.setZ(tmp);
				cc.setV(tmp);
				cc.setC(srcObj.operand + ~dstObj.operand + 1);

				break;
			case BCC:
				if(cc.c == 0){
					reg.set(7,getPcAddOff2(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case BNE:
				if(cc.z == 0){
					reg.set(7,getPcAddOff2(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case BEQ:
				if(cc.z == 1){
					reg.set(7,getPcAddOff2(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case BGT:
				if(cc.z == 0 && cc.n == cc.v){
					reg.set(7,getPcAddOff2(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case BHI:
				if(cc.c == 0 && cc.z == 0){
					reg.set(7,getPcAddOff2(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				}
				break;
			case SYS:	
				switch(getOctal(opcode,5)){
				case 0:
					int sub = getMem();
					tmp = reg.get(7);
					execute(sub, sub+1);

					reg.set(7, tmp);

					break;
				case 1:
					System.exit(0);
					break;
				case 4:
					int textMemNum = getMem();
					int operand2 = getMem();

					for(int i=0;i<operand2;i++){
						getChar(getMemory1(textMemNum),2);
						textMemNum++;
					}
					break;
				}
				break;
			case SUB:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				
				tmp = (dstObj.operand - srcObj.operand);
				
				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else if(dstObj.flgAddress){
					setMemory2(dstObj.address, tmp);
				}else{
					setMemory2(dstObj.address, tmp);
				}
				
				cc.setN(tmp);
				cc.setZ(tmp);
				cc.setV(tmp);
				cc.setC(srcObj.operand + ~dstObj.operand + 1);

				break;
			case SETD:
				break;
			case WORD:
				break;
			}

			//debug用
			if(dbgFlg == 1){
				printDebug();
			}

			//メモリダンプ用
			if(mmrFlg == 1){
				printMemory();
			}
		}
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
							}
							break;
						}
						break;
					case 1:
						switch(getOctal(opcode,3)){
						case 0:
							mnemonic = Mnemonic.BNE;
							break;
						case 4:
							mnemonic = Mnemonic.BEQ;
							break;
						}
						break;
					case 3:
						switch(getOctal(opcode,3)){
						case 0:
							mnemonic = Mnemonic.BGT;
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
						case 7:
							mnemonic = Mnemonic.TST;
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
				}
				break;
			case 1:
				switch(getOctal(opcode,1)){
				case 1:
					mnemonic = Mnemonic.MOVB;
					break;
				case 0:
					switch(getOctal(opcode,2)){
					case 1:
						switch(getOctal(opcode,3)){
						case 0:
							mnemonic = Mnemonic.BHI;
							break;
						}
						break;
					case 3:
						switch(getOctal(opcode,3)){
						case 0:
							mnemonic = Mnemonic.BCC;
							break;
						}
						break;
					case 4:
						switch(getOctal(opcode,3)){
						case 4:	
							switch(getOctal(opcode,4)){
							case 0:	
								mnemonic = Mnemonic.SYS;
								break;
							}
							break;
						}
						break;
					}
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

			if(mnemonic == null){
				mnemonic = Mnemonic.WORD;
			}

		return mnemonic;
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

	//フィールド取得（PC+オフセット*2 6bit）
	FieldDto getXX(int first,int second){
		FieldDto operand = new FieldDto();
		operand.setStr("$0x" + String.format("%02x",(((first << 3) + second) * 2 + reg.get(7))));
		operand.setAddress(((first << 3) + second) * 2 + reg.get(7));

		return operand;
	}

	//フィールド取得（PC+オフセット*2 8bit）
	FieldDto getPcAddOff2(int first,int second,int third){
		FieldDto operand = new FieldDto();
		int tmp = (first << 6) + (second << 3) + third;
		if(tmp > (0xFF+1)){
			tmp = tmp -(0xFF+1);
		}
		tmp = tmp * 2 + reg.get(7);

		operand.setStr("$0x" + String.format("%03x",tmp));
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
	FieldDto getField(int first, int second){
		FieldDto field = getField(first, second, 0);
		return field;
	}

	//フィールド取得（dst,src）
	FieldDto getField(int first, int second, int flgByte){

		//返り値
		FieldDto field = new FieldDto();

		//ワーク
		short opcodeShort;
		int opcodeInt;

		switch(second){
		case 0:
		case 1:
		case 2:
		case 3:
		case 4:
		case 5:
		case 6:
			switch(first){
			case 0:
				//レジスタ
				//registerにオペランドがある。
				field.setStr(getRegisterName(second));
				field.setOperand(reg.get(second));
				field.setReg(second);
				break;
			case 1:
				//レジスタ間接
				//registerにオペランドのアドレスがある。
				field.setStr("(" + getRegisterName(second) + ")");
				field.setOperand(getMemory2(reg.get(second)));
				field.setAddress(reg.get(second));
				break;
			case 2:
				//自動インクリメント
				//registerにオペランドのアドレスがあり、命令実行後にregisterの内容をインクリメントする。
				field.setStr("(" + getRegisterName(second) + ")+");
				field.setOperand(getMemory2(reg.get(second)));
				field.setAddress(reg.get(second));
				if(exeFlg == 1){
					if(flgByte == 1){
						reg.add(second,1);
					}else{
						reg.add(second,2);
					}
				}
				break;
			case 3:
				//自動インクリメント間接
				//registerにオペランドへのポインタのアドレスがあり、命令実行後にregisterの内容を2だけインクリメントする。
				field.setStr("*(" + getRegisterName(second) + ")+");
				field.setOperand(getMemory2(getMemory2(reg.get(second))));
				field.setAddress(getMemory2(reg.get(second)));
				if(exeFlg == 1){
					reg.add(second,4);
				}
				break;
			case 4:
				//自動デクリメント
				//命令実行前にregisterをデクリメントし、それをオペランドのアドレスとして使用する。
				if(exeFlg == 1){
					if(flgByte == 1){
						reg.add(second,-1);
					}else{
						reg.add(second,-2);
					}
				}
				field.setStr("-(" + getRegisterName(second) + ")");
				field.setOperand(getMemory2(reg.get(second)));
				field.setAddress(reg.get(second));
				break;
			case 5:
				//自動デクリメント間接
				//命令実行前にregisterを2だけデクリメントし、それをオペランドへのポインタのアドレスとして使用する。
				if(exeFlg == 1){
					reg.add(second,-4);
				}
				field.setStr("*-(" + getRegisterName(second) + ")");
				field.setOperand(getMemory2(getMemory2(reg.get(second))));
				field.setAddress(getMemory2(reg.get(second)));
				break;
			case 6:
				//インデックス
				//register+Xがオペランドのアドレス。Xはこの命令に続くワード。
				opcodeInt = getMem();
				field.setStr(String.format("%01o",opcodeInt) + "(" + getRegisterName(second) + ")");
				field.setOperand(getMemory2(reg.get(second) + opcodeInt));
				field.setAddress(reg.get(second) + opcodeInt);
				break;
			case 7:
				//インデックス間接
				//register+Xがオペランドへのポインタのアドレス。Xはこの命令に続くワード。
				opcodeInt = getMem();
				field.setStr("*" + String.format("%01o",opcodeInt) + "(" + getRegisterName(second) + ")");
				field.setOperand(getMemory2(getMemory2(reg.get(second) + opcodeInt)));
				field.setAddress(getMemory2(reg.get(second) + opcodeInt));
				break;
			}
			break;

		case 7:
			switch(first){
			case 2:
				//イミディエート
				//オペランドは命令内にある。
				opcodeShort = (short)getMem();
				if(opcodeShort < 0){
					field.setStr("$" + "-" + String.format("%01o",~(opcodeShort - 1)));
				}else{
					field.setStr("$" + String.format("%01o",opcodeShort));
				}
				field.setOperand((int)opcodeShort); //ちょっとあやしい？
				break;
			case 3:
				//絶対
				//オペランドの絶対アドレスが命令内にある。
				opcodeShort = (short)getMem();
				if(opcodeShort < 0){
					field.setStr("*$" + "-" + String.format("%01o",~(opcodeShort - 1)));
				}else{
					field.setStr("*$" + String.format("%01o",opcodeShort));
				}
				field.setOperand((int)opcodeShort); //ちょっとあやしい？
				field.setAddress((int)opcodeShort);
				break;
			case 6:
				//相対
				//命令に続くワードの内容 a を PC+2 に加算したものをアドレスとして使用する。
				opcodeInt = getMem();
				int tmp = opcodeInt + reg.get(7);
				if(tmp >= 0x10000){
					tmp = tmp - 0x10000;
				}
				field.setStr("0x" + String.format("%02x",tmp));
				field.setOperand(getMemory2(tmp));
				field.setAddress(tmp);
				break;
			case 7:
				//相対間接
				//命令に続くワードの内容 a を PC+2 に加算したものをアドレスのアドレスとして使用する。
				opcodeInt = getMem();
				field.setStr("*0x" + String.format("%02x",(opcodeInt + reg.get(7))));
				field.setOperand(getMemory2(opcodeInt + reg.get(7))); //ちょっとあやしい？
				field.setAddress(getMemory2(opcodeInt + reg.get(7)));
				break;
			}
			break;
		}	

		return field;
	}
	
	void printDebug(){
		System.out.print("\n");
		System.out.println("-s-register-start------------");

		System.out.print(" r0=" + String.format("%04x",reg.get(0)));
		System.out.print(" r1=" + String.format("%04x",reg.get(1)));
		System.out.print(" r2=" + String.format("%04x",reg.get(2)));
		System.out.print(" r3=" + String.format("%04x",reg.get(3)));
		System.out.print(" r4=" + String.format("%04x",reg.get(4)));
		System.out.print(" r5=" + String.format("%04x",reg.get(5)));
		System.out.print(" sp=" + String.format("%04x",reg.get(6)));
		System.out.println(" pc=" + String.format("%04x",reg.get(7)));

		System.out.print(" n=" + cc.n);
		System.out.print(" z=" + cc.z);
		System.out.print(" v=" + cc.v);
		System.out.println(" c=" + cc.c);

		System.out.println("-s-register-end-------------");
	}
	
	void printMemory(){
		System.out.print("--memory-start-------------");
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
		System.out.println("\n--memory-end-------------");
	}
}

enum Mnemonic { 
	RTT, JMP, RTS, JSR, CLR, TST, MOV, CMP, BCC, SYS, SETD, WORD, BNE, BEQ, INC, DEC, BGT, SUB, BHI, MOVB
};

class FieldDto{

	String str;
	int operand;
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