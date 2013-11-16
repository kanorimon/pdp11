package pdp11;

public class Process implements Cloneable{
	
	//���z�A�h���X���
	VirtualAddressSpace vas;
	
	//�v���Z�XID
	int pid;
	
	//�q�v���Z�X�t���O
	boolean flgChildProcess;
	
	//wait����Register
	int r0;
	int r1;
	int r2;
	int r3;
	int r4;
	int r5;
	int r6;
	int r7;
	
	//�e�̃v���Z�XID
	int parentPid;
	
	//�q�̃v���Z�XID
	int childPid;
	int childExitNo;
	
	
	//�R���X�g���N�^
	Process(int processNo, boolean inFlgChildProcess){
		vas = new VirtualAddressSpace();
		
		//�v���Z�XID������
		pid = processNo;
		
		//�q�v���Z�X�t���O
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
