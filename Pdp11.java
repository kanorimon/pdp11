package pdp11;

public class Pdp11{
	/*
	 * 定数
	 */
	final static int MAX_PROCESS = 16;

	/*
	 * メイン処理
	 */
	public static void main(String[] args){
		//オプション
		int flgDebugMode = 0;
		boolean flgDismMode = false;
		boolean flgExeMode = false;
		boolean flgMemoryDump = false;
		int flgOsMode = 0;

		//オプション設定
		int i = 0;
		while(true){
			if(!(args[i].substring(0,1).equals("-"))){
				break;
			}
			//デバッグモード（システムコールのみ）
			if(args[i].equals("-s")){
				flgDebugMode = 1;
			}
			//デバッグモード（すべて）
			if(args[i].equals("-v")){
				flgDebugMode = 2;
			}
			//デバッグモード（メモリダンプ）
			if(args[i].equals("-m")){
				flgMemoryDump = true;
			}
			//Linuxモード
			if(args[i].equals("-l")){
				flgOsMode = 1;
			}
			//逆アセンブルモード
			if(args[i].equals("-d")){
				flgDismMode = true;
			}
			//実行モード
			if(args[i].equals("-e")){
				flgExeMode = true;
			}
			i++;
		}
		
		//オプション指定がなければ逆アセンブルモード＋実行モード
		if(flgDebugMode==0 && !flgDismMode && !flgExeMode && !flgMemoryDump){
			flgDismMode = true;
			flgExeMode = true;
		}
		
		//カーネル作成
		Kernel kernel = new Kernel(MAX_PROCESS,flgDebugMode, flgDismMode, flgExeMode, flgMemoryDump, flgOsMode);
		
		//実行
		kernel.start(args,i);

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
