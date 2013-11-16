package pdp11;

public class Process implements Cloneable{
	
	VirtualAddressSpace vas; //仮想アドレス空間
	int pid; //プロセスID
	boolean flgChildProcess; //子プロセスフラグ
	
	//wait時のRegister
	int r0;
	int r1;
	int r2;
	int r3;
	int r4;
	int r5;
	int r6;
	int r7;
	
	//親のプロセスID
	int parentPid;
	
	//子のプロセスID
	int childPid;
	int childExitNo;
	
	//コンストラクタ
	Process(int processNo, boolean inFlgChildProcess){
		vas = new VirtualAddressSpace();
		pid = processNo; //プロセスID初期化
		flgChildProcess = inFlgChildProcess; //子プロセスフラグ
	}

	public Object clone() {
		Process cloneProcess = null;
		try {
			cloneProcess = (Process)super.clone();
			vas = (VirtualAddressSpace) vas.clone();
		} catch (CloneNotSupportedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return cloneProcess;  
	}
	
}
