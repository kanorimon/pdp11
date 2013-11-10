package pdp11;

public class Process implements Cloneable{
	
	//仮想アドレス空間
	VirtualAddressSpace vas;
	
	//プロセスID
	int pid;
	
	//子プロセスフラグ
	boolean flgChildProcess;
	
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
		
		//プロセスID初期化
		pid = processNo;
		
		//子プロセスフラグ
		flgChildProcess = inFlgChildProcess;
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
