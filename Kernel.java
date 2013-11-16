package pdp11;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.Stack;

/*
 * Kernel Class
 */
public class Kernel{
	
	/*�ϐ���`------------------------------------------*/
	
	Process[] proc; //�v���Z�X
	int nowProcessNo; //���ݏ������̃v���Z�XNo
	
	Register reg; //���W�X�^
	FileDescriptor fd; //�t�@�C���f�B�X�N���v�^
	ConditionCode cc; //�R���f�B�V�����R�[�h
	Signal signal; //�V�O�i��
	
	VirtualAddressSpace pva; //�e�v���Z�X
	
	int strnum; //�o�͗p���[�e�B���e�B
	Stack<Byte> argStack; //�X�^�b�N�����p
	
	int flgDebugMode; //�f�o�b�O���[�h
	boolean flgDismMode; //�t�A�Z���u�����[�h
	boolean flgExeMode; //���s���[�h
	boolean flgMemoryDump; //�f�o�b�O���[�h�i�������_���v�j
	int flgOsMode; //OS���[�h
	

	/*�R���X�g���N�^------------------------------------------*/

	Kernel(int maxProcess,int inFlgDebugMode,boolean inFlgDismMode, boolean inFlgExeMode,boolean inFlgMemoryDump,int inFlgOsMode){
		proc = new Process[maxProcess];
		
		nowProcessNo = 0; //�v���Z�XNo��0����X�^�[�g

		flgDebugMode = inFlgDebugMode;
		flgDismMode = inFlgDismMode;
		flgExeMode = inFlgExeMode;
		flgMemoryDump = inFlgMemoryDump;
		flgOsMode = inFlgOsMode;

		reset();
		}

	
	/*���[�e�B���e�B�֐�------------------------------------------*/

	//���W�X�^�֘A����
	void reset(){
		reg = new Register(); //���W�X�^����
		fd = new FileDescriptor(); //�t�@�C���f�B�X�N���v�^����
		cc = new ConditionCode(); //�R���f�B�V�����R�[�h����
		signal = new Signal(); //�V�O�i������
	}
	
	//�J�[�l���X�^�[�g
	void start(String[] args,int argsNo){
		createProcess(args,argsNo,false); //�v���Z�X����
		if(flgDismMode) dissAssemble(); //�t�A�Z���u��
		setExecute(flgDebugMode, flgMemoryDump, argStack); //���s�O�ݒ�
		if(flgExeMode) execute(); //���s
	}
	
	//�v���Z�X����
	void createProcess(String[] args,int argsNo,boolean inFlgChildProcess){
		proc[nowProcessNo] = new Process(nowProcessNo,inFlgChildProcess); //�v���Z�X�C���X�^���X�쐬
		setStack(args, argsNo); //�X�^�b�N�ݒ�
		readBinary(args[argsNo]); //�v���Z�X�Ƀo�C�i����ǂݍ���
	}
	
	//�t�A�Z���u���Ăяo��
	void dissAssemble(){
		disassemble(0,proc[nowProcessNo].vas.textSize);
	}
	
	//�C���^�v���^���s�O�ݒ�
	public void setExecute(int debugFlg,boolean memoryFlg, Stack<Byte> args){
		flgDebugMode = debugFlg; //�f�o�b�O�t���O
		flgMemoryDump = memoryFlg; //�f�o�b�O�t���O
		
		reg.reset(); //���W�X�^������
		cc.reset(); //�R���f�B�V�����R�[�h������
		signal.reset(); //�V�O�i��������

		//�������X�^�b�N�ɐݒ�
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
	
	//���s�Ăяo��
	void execute(){
		execute(0, proc[nowProcessNo].vas.textSize);
	}
	
	//�X�^�b�N�̐ݒ�iString[]�j
	void setStack(String[] args,int argsNo){
		
		//������byte�z��ɕϊ�
		ArrayList<byte[]> arg = new ArrayList<byte[]>();
		int argSize = 0;
		int argCnt = 0;
		for(;argsNo<args.length;argsNo++){
			arg.add(args[argsNo].getBytes());
			argSize = argSize + arg.get(argCnt).length + 1;
			argCnt++;
		}
		
		//������Byte��Stack�ɐςݍ���
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
	
	//�X�^�b�N�̐ݒ�iArrayList<String>�j
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
	
	//�o�C�i���ǂݍ���
	void readBinary(String strFileName){
		File file = new File(strFileName);
		Path fileName = file.toPath();

		//�t�@�C�����e�擾
		byte[] bf = null;
		try {
	        bf = java.nio.file.Files.readAllBytes(fileName);
		} catch (IOException e) {
			e.printStackTrace();
		}

		//���z�������Ƀ��[�h
		proc[nowProcessNo].vas = new VirtualAddressSpace(bf);
	}
	
	//8�i���ɕϊ��������߂̔C�ӂ̉ӏ����擾
	int getOctal(int dec,int index){
		int val = Integer.parseInt(String.format("%06o",dec).substring(index, index+1));
		return val;
	}

	//ASCII�R�[�h�ɕϊ������f�[�^��\��
	void printChar(int dec){
		System.out.print((char)Integer.parseInt(String.format("%02x",dec),16));
	}

	//�w�肵�����߂��o��
	void printOpcode(int opcode){
		System.out.print(String.format("%04x", opcode));
		System.out.print(" ");
	}
	
	//2�o�C�g�P�ʂŃ��g���G���f�B�A���𔽓]����10�i���Ŏ擾
	int getMemory2(int start){
		return proc[nowProcessNo].vas.getMemory2(start);
	}

	//1�o�C�g�P�ʂŎw��ӏ��̃��������擾
	int getMemory1(int start){
		return proc[nowProcessNo].vas.getMemory1(start);
	}

	//2�o�C�g�P�ʂŎw��ӏ��̃��������X�V
	void setMemory2(int add,int src){
		proc[nowProcessNo].vas.setMemory2(add,src);
	}

	//1�o�C�g�P�ʂŎw��ӏ��̃��������X�V
	void setMemory1(int add,int src){
		proc[nowProcessNo].vas.setMemory1(add,src);
	}
	
	//��������̃f�[�^���擾���āAPC+2����
	int getMem(){
		int opcode = getMemory2(reg.get(7));

		//�t�A�Z���u���̏ꍇ�͏o��
		if(flgExeMode){
			if(flgDebugMode>1) printOpcode(opcode);
		}else{
			printOpcode(opcode);
			strnum++;
		}
		
		reg.add(7,2); //PC+2

		return opcode;
	}

	//8�i������10�i���ɕϊ�
	int getDex(int first,int second){
		return Integer.parseInt(Integer.toString(first * 10 + second), 8);
	}
		
	//���W�X�^���̎擾
	String getRegisterName(int no){
		if(no == 7){
			return "pc";
		}else if(no == 6){
			return "sp";
		}else{
			return "r" + no;
		}
	}

	//�X�^�b�N�ς�
	void pushStack(int n){
		reg.add(6,-2);
		setMemory2(reg.get(6),n);
	}

	
	/*�t�A�Z���u���֐�------------------------------------------*/

	void disassemble(int start, int end){
		flgExeMode = false; //���s���[�h�I�t

		reg.reset(); //���W�X�^������

		//�t�A�Z���u��
		for(reg.set(7, start);reg.get(7)<end;){

			//�v���O�����J�E���^���o��
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
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case ADD:
				mnemonic = "add";
				srcOperand = getField(getOctal(opcode,2),getOctal(opcode,3)).str;
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
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
			case ASL:
				mnemonic = "asl";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case ASR:
				mnemonic = "asr";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BCC:
				mnemonic = "bcc";
				srcOperand = "";
				dstOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BCS:
				mnemonic = "bcs";
				srcOperand = "";
				dstOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BEQ:
				mnemonic = "beq";
				srcOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = "";
				break;
			case BGE:
				mnemonic = "bge";
				srcOperand = "";
				dstOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BGT:
				mnemonic = "bgt";
				srcOperand = "";
				dstOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BHI:
				mnemonic = "bhi";
				srcOperand = "";
				dstOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
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
			case BLE:
				mnemonic = "ble";
				srcOperand = "";
				dstOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BLOS:
				mnemonic = "blos";
				srcOperand = "";
				dstOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BLT:
				mnemonic = "blt";
				srcOperand = "";
				dstOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BMI:
				mnemonic = "bmi";
				srcOperand = "";
				dstOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BNE:
				mnemonic = "bne";
				srcOperand = "";
				dstOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BPL:
				mnemonic = "bpl";
				srcOperand = "";
				dstOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BR:
				mnemonic = "br";
				srcOperand = "";
				dstOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case BVS:
				mnemonic = "bvs";
				srcOperand = "";
				dstOperand = getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
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
			case CMP:
				mnemonic = "cmp";
				srcOperand = getField(getOctal(opcode,2),getOctal(opcode,3)).str;
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case CMPB:
				mnemonic = "cmpb";
				srcOperand = getField(getOctal(opcode,2),getOctal(opcode,3)).str;
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
			case DIV:
				mnemonic = "div";
				srcOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = getRegisterName(getOctal(opcode,3));
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
			case JMP:
				mnemonic = "jmp";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case JSR:
				mnemonic = "jsr";
				srcOperand = getRegisterName(getOctal(opcode,3));
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
			case MUL:
				mnemonic = "mul";
				srcOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				dstOperand = getRegisterName(getOctal(opcode,3));
				break;
			case NEG:
				mnemonic = "neg";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case ROL:
				mnemonic = "rol";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case ROR:
				mnemonic = "ror";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case RTS:
				mnemonic = "rts";
				srcOperand = "";
				dstOperand = getRegisterName(getOctal(opcode,5));
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
				srcOperand = getRegisterName(getOctal(opcode,3));
				dstOperand = getOffset6(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case SUB:
				mnemonic = "sub";
				srcOperand = getField(getOctal(opcode,2),getOctal(opcode,3)).str;
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case SWAB:
				mnemonic = "swab";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case SXT:
				mnemonic = "sxt";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case SYS:
				if(getDex(getOctal(opcode,4),getOctal(opcode,5)) == 0) getMem();
				mnemonic = "sys";
				srcOperand = "";
				dstOperand = String.valueOf(getOctal(opcode,5));
				break;
			case TST:
				mnemonic = "tst";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case TSTB:
				mnemonic = "tstb";
				srcOperand = "";
				dstOperand = getField(getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			case WORD:
				mnemonic = ".word";
				srcOperand = "";
				dstOperand = getNormal(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).str;
				break;
			}

			//�o��
			for(;strnum<3;strnum++) System.out.print("     ");

			System.out.print(" ");
			System.out.print(mnemonic);

			for(int j=mnemonic.length();j<9;j++) System.out.print(" ");

			System.out.print(srcOperand);

			if(!(srcOperand.equals("")) && !(dstOperand.equals(""))) System.out.print(", ");

			System.out.print(dstOperand + "\n");
		}
	}


	/*�C���^�v���^�֐�------------------------------------------*/

	//���s�Ăяo��
	public void execute(int start, int end){
		execute(start, end, false,false);
	}

	//���s�Ăяo��
	public void execute(int start, int end,boolean endFlg){
		execute(start, end, endFlg,false);
	}

	//�C���^�v���^
	public void execute(int start, int end, boolean endFlg,boolean forkFlg){
		flgExeMode = true; //���s���[�h�I��
		
		if(!forkFlg) reg.set(7,start); //PC��������
		if(!endFlg) end = 65536;

		for(;reg.get(7)<end;){
			if(flgDebugMode>1) printDebug(); //���W�X�^�E�t���O�o��
			if(flgMemoryDump) printMemory(); //�������_���v�o��

			//���[�N
			FieldDto srcObj;
			FieldDto dstObj;
			int tmp = 0;
			
			int opcode = getMem(); //���ߎ擾
			Mnemonic nic = getMnemonic(opcode); //�j�[���j�b�N�擾
			
			switch(nic){
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
				
				cc.set((reg.get(getOctal(opcode,3)) << 1 >>> 16)>0, //TODO
						reg.get(getOctal(opcode,3))==0, 
						((ashReg << 16 ) >>> 31) != ((reg.get(getOctal(opcode,3)) << 16) >>> 31), //TODO
						false); //TODO
				
				break;
			case ASHC: //TODO
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
				
				cc.set(tmp>0, //TODO
						tmp==0, 
						(ashcTmp >>> 31) != (tmp  >>> 31), //TODO
						false); //TODO
				
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
			case BCC:
				if(cc.c == false) reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				break;
			case BCS:
				if(cc.c == true) reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				break;
			case BEQ:
				if(cc.z == true) reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				break;
			case BGE:
				if(cc.n == cc.v) reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				break;
			case BGT:
				if(cc.z == false && cc.n == cc.v) reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				break;
			case BHI:
				if(cc.c == false && cc.z == false) reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
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
			case BIS:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));

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
				
				cc.set(false, //TODO
						tmp == 0, 
						false, 
						cc.c);

				break;
			case BIT:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				tmp = srcObj.operand & dstObj.operand;
				
				cc.set(false, //TODO 
						tmp==0, 
						false, 
						cc.c);

				break;
			case BITB:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				tmp = srcObj.operand & dstObj.operand;
				tmp = tmp << 24 >>> 24;
				
				cc.set(false, //TODO 
						tmp==0, 
						false, 
						cc.c);

				break;
			case BLE:
				if(cc.z == true || cc.n != cc.v) reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				break;
			case BLOS:
				if(cc.c == true || cc.z == true) reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				break;
			case BLT:
				if(cc.n != cc.v) reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				break;
			case BMI:
				if(cc.n == true) reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				break;
			case BNE:
				if(cc.z == false) reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				break;
			case BPL:
				if(cc.n == false) reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				break;
			case BR:
				reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
				break;
			case BVS:
				if(cc.v == true) reg.set(7,getOffset(getOctal(opcode,3),getOctal(opcode,4),getOctal(opcode,5)).address);
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
			case CMP:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				tmp = (srcObj.operand << 16 >>> 16) - (dstObj.operand << 16 >>> 16);
				
				cc.set((tmp << 16 >>> 31)>0, 
						tmp==0, 
						getSubOverflow(srcObj.operand, dstObj.operand, tmp), 
						getSubBorrow(srcObj.operand, dstObj.operand, tmp));

				break;
			case CMPB:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3), true);
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5), true);

				tmp = (srcObj.operand << 24 >>> 24) - (dstObj.operand << 24 >>> 24);
				
				cc.set((tmp << 1 >>> 16)>0, 
						tmp==0, 
						getSubOverflow(srcObj.operand << 24 >>> 24, dstObj.operand << 24 >>> 24, tmp), 
						getSubBorrow(srcObj.operand << 24 >>> 24, dstObj.operand << 24 >>> 24, tmp));

				break;
			case DEC:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				
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
			case DIV: 
				int divR1 = reg.get(getOctal(opcode,3)) << 16;
				int divR2 = reg.get(getOctal(opcode,3)+1);
				
				int divValue = divR1 + divR2;
				
				srcObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				
				reg.set(getOctal(opcode,3), divValue / srcObj.operand);
				reg.set(getOctal(opcode,3)+1, divValue % srcObj.operand);

				cc.set((reg.get(getOctal(opcode,3)) >> 15)>0, 
						reg.get(getOctal(opcode,3))==0, 
						srcObj.operand==0, //TODO
						srcObj.operand==0);
				
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
			case JSR:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				pushStack(reg.get(getOctal(opcode,3)));
				reg.set(getOctal(opcode,3),reg.get(7));
				reg.set(7, dstObj.address);

				cc.set(cc.n, cc.z, cc.v, cc.c);

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
			case MOVB:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3), true);
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5), true);
				
				if(srcObj.flgRegister){
					if(dstObj.flgRegister){
						//���[�h0�̏ꍇ�A�����g�����s��
						tmp = reg.get(srcObj.register) << 24;
						tmp = tmp >> 24;
						reg.set(dstObj.register, tmp);
					}else if(dstObj.flgAddress){
						tmp = reg.get(srcObj.register);
						setMemory1(dstObj.address, tmp);
					}
				}else if(srcObj.flgAddress){
					if(dstObj.flgRegister){
						//���[�h0�̏ꍇ�A�����g�����s��
						tmp = getMemory1(srcObj.address) << 24;
						tmp = tmp >> 24;
						reg.set(dstObj.register, tmp);
					}else if(dstObj.flgAddress){
						tmp = getMemory1(srcObj.address);
						setMemory1(dstObj.address, tmp);
					}
				}else{
					if(dstObj.flgRegister){
						//���[�h0�̏ꍇ�A�����g�����s��
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
			case RTS:
				reg.set(7,reg.get(getOctal(opcode,5)));
				reg.set(getOctal(opcode,5),getMemory2(reg.get(6)));
				reg.add(6,2);
				
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
			case RTT:
				break;
			case SETD:
				break;
			case SEV:
				cc.set(cc.n, cc.z, true, cc.c);
				break;
			case SOB:
				short tmpShort = (short)(reg.get(getOctal(opcode,3)) - 1);
				reg.set(getOctal(opcode,3),((reg.get(getOctal(opcode,3)) - 1) << 16) >>> 16);
				if(tmpShort != 0) reg.set(7,getOffset6(getOctal(opcode,4),getOctal(opcode,5)).address);
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
			case SYS:
				
				int val1;
				int val2;

				switch(getDex(getOctal(opcode,4),getOctal(opcode,5))){
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
						//���s
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
							
							if(flgExeMode) execute(0, proc[nowProcessNo].vas.textSize,false,true);
						}
					}else{
						System.exit(0);
					}
					break;
				case 2: //fork
					if(flgDebugMode>0) System.out.println("\n fork:");

					//���z��������ޔ�
					proc[nowProcessNo].r0 = reg.get(0);
					proc[nowProcessNo].r1 = reg.get(1);
					proc[nowProcessNo].r2 = reg.get(2);
					proc[nowProcessNo].r3 = reg.get(3);
					proc[nowProcessNo].r4 = reg.get(4);
					proc[nowProcessNo].r5 = reg.get(5);
					proc[nowProcessNo].r6 = reg.get(6);
					proc[nowProcessNo].r7 = reg.get(7);
					proc[nowProcessNo].childPid = nowProcessNo+1;
					
					proc[nowProcessNo+1] = (Process) proc[nowProcessNo].clone();

					nowProcessNo++;
					reg.set(0, 0);

					for(int k=3;k<16;k++) fd.clear(k);
					
					proc[nowProcessNo].flgChildProcess = true;
					proc[nowProcessNo].pid = nowProcessNo;
					proc[nowProcessNo].parentPid = nowProcessNo-1;
					
					//���s
					if(flgExeMode) execute(0, proc[nowProcessNo].vas.textSize,false,true);
					
					break;
				case 3: //read
					val1 = getMem(); //�ǂݍ��݈ʒu
					val2 = getMem();  //�ǂݍ��݃T�C�Y
					
					if(flgDebugMode>0) System.out.print("\n read:" + reg.get(0) + "," + val1 + "," + val2);
					
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
					val1 = getMem(); //�������݌��f�[�^�ʒu
					val2 = getMem(); //�������݃T�C�Y

					if(flgDebugMode>0) System.out.print("\n write:" + reg.get(0) + "," + val1 + "," + val2);

					int i = 0;
					if(fd.isFile(reg.get(0))){
			
						BlockFile outFile = (BlockFile) fd.get(reg.get(0)); 
				        File file = new File(outFile.inode.toString());
				        
						//�t�@�C�����ݒ�
						Path fileName = file.toPath();
						
						//�t�@�C�����e�擾
						byte[] beforeByte = null;
						try {
							beforeByte = java.nio.file.Files.readAllBytes(fileName);
						} catch (IOException e) {
							e.printStackTrace();
						}

						int writeSize = beforeByte.length;
						if(beforeByte.length < fd.getOffset(reg.get(0)) + val2)	writeSize =  fd.getOffset(reg.get(0)) + val2;
						byte[] writeByte = new byte[writeSize];

						if(beforeByte.length < fd.getOffset(reg.get(0))){
							for(i=0;i<beforeByte.length;i++) writeByte[i] = beforeByte[i];
							for(i=beforeByte.length;i<fd.getOffset(reg.get(0));i++) writeByte[i] = 0;
						}else{
							for(i=0;i<fd.getOffset(reg.get(0));i++) writeByte[i] = beforeByte[i];
						}

						for(i=fd.getOffset(reg.get(0));i<fd.getOffset(reg.get(0))+val2;i++){
					        writeByte[i] = (byte) (getMemory1(val1) << 24 >>> 24);
							val1++;
						}
						
						for(i=fd.getOffset(reg.get(0))+val2;i<beforeByte.length;i++) writeByte[i] = beforeByte[i];
						
				        try {
							java.nio.file.Files.write(fileName, writeByte);
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
					
					if(openFile.isFile()){
						reg.set(0,fd.open(fd.search(), openFile.toPath()));
						cc.set(cc.n, cc.z, cc.v, false);
						
					}else{
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

					//TODO �G���[�������Ȃ���
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
					
					//TODO �G���[�������Ȃ���
					cc.set(cc.n, cc.z, cc.v, false);
					
					break;
				case 10: //unlink
					File unlinkFile = getFile(getMem(),"unlink");
					unlinkFile.delete();
					
					reg.set(0, 0);
					
					//TODO �G���[�������Ȃ���
					cc.set(cc.n, cc.z, cc.v, false);
					
					break;
				case 11: //exec
					if(flgDebugMode>0) System.out.print("\n exec");

					String execTmp1 = getFileName(getMem());
					int argsIndex = getMem();
					ArrayList<String> execArgs = new ArrayList<String>();
					
					while(true){
						if(getMemory2(argsIndex) == 0) break;
						execArgs.add(getFileName(getMemory2(argsIndex)));
						argsIndex += 2;
					}
					
					setStack(execArgs); //�X�^�b�N�ݒ�
					readBinary(execTmp1); //�v���Z�X�Ƀo�C�i����ǂݍ���
					setExecute(flgDebugMode, flgMemoryDump, argStack); //���s�O�ݒ�

					if(flgExeMode) execute(0, proc[nowProcessNo].vas.textSize);

					//TODO �G���[�������Ȃ���
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

					getMem();
					
					if(statFile.isFile()){
						
					}else{
						cc.set(cc.n, cc.z, cc.v, true);
					}
					
					break;
				case 19: //lseek
					val1 = getMem(); //�V�[�N�T�C�Y
					val2 = getMem(); //���[�h
					
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
					val1 = getMem(); //�V�[�N�T�C�Y
					val2 = getMem(); //���[�h

					if(flgDebugMode>0) System.out.print("\n signal:" + reg.get(0) + "," + val1 + "," + val2);

					signal.set(val1, val2);
					reg.set(0,0);

					break;
				}
				break;
			case TST:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				cc.set((dstObj.operand << 16 >>> 31)>0, (dstObj.operand << 16 >>> 16)==0, false, false);
				break;
			case TSTB:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5), true);
				cc.set((dstObj.operand << 1 >>> 15)>0, (dstObj.operand << 24 >>> 24)==0, false, false);
				break;
			case WORD:
				break;
			}
		}
	}
	

	/*�t�@�C���֐�------------------------------------------*/
	
	//�t�@�C���ԋp
	File getFile(int val,String debugName){
		if(flgDebugMode>0) System.out.print("\n " + debugName); //�f�o�b�O�p
		File file = new File(getFileName(val)); //�t�@�C�����ݒ�
		return file;
	}
	
	//�t�@�C�����ԋp
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

	
	/*�I�[�o�[�t���[�֐�------------------------------------------*/

	//���Z�I�[�o�[�t���[����
	boolean getAddOverflow(int src, int dst, int val){
		boolean addV = false;
		if((dst << 1 >>> 16) == (src << 1 >>> 16)){
			if((dst << 1 >>> 16) != (val << 1 >>> 16)) addV = true;
		}
		return addV;
	}

	//���Z�I�[�o�[�t���[����
	boolean getSubOverflow(int src, int dst, int val){
		boolean subV = false;
		if((dst << 16 >>> 31) != (src << 16 >>> 31)){
			if((dst << 16 >>> 31) == (val << 16 >>> 31)) subV = true;
		}
		return subV;
	}

	//���Z�L�����[����
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
	
	//���Z�{���[����
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


	/*�t�B�[���h�֐�------------------------------------------*/
	
	//�t�B�[���h�擾�iPC+�I�t�Z�b�g*2 8bit�i�����t�j�j
	FieldDto getOffset(int first,int second,int third){
		FieldDto operand = new FieldDto();
		int tmp = (first << 6) + (second << 3) + third;
		byte tmpByte = (byte)tmp;
		tmp = reg.get(7) + tmpByte * 2;
		
		operand.setStr("0x" + String.format("%x",tmp));
		operand.setAddress(tmp);

		return operand;
	}

	//�t�B�[���h�擾�iPC-�I�t�Z�b�g*2 6bit�i�����Ȃ��A���̐��l�j�j
	FieldDto getOffset6(int first,int second){
		FieldDto operand = new FieldDto();
		int tmp = (first << 3) + second;
		tmp = reg.get(7) - tmp * 2;

		operand.setStr("0x" + String.format("%x",tmp));
		operand.setAddress(tmp);

		return operand;
	}

	//�t�B�[���h�擾�i8�i�� 6bit�j
	FieldDto getNormal(int first,int second,int third){
		FieldDto operand = new FieldDto();
		operand.setStr(String.format("%o",(first << 6) + (second << 3) + third));
		operand.setAddress(((first << 3) + second) * 2 + reg.get(7));

		return operand;
	}

	//�t�B�[���h�擾�idst,src�j
	FieldDto getField(int mode, int regNo){
		return getField(mode, regNo, false);
	}

	//�t�B�[���h�擾�idst,src�j
	FieldDto getField(int mode, int regNo, boolean byteFlg){
		FieldDto field = new FieldDto(); //�Ԃ�l

		//���[�N
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
				//���W�X�^
				//register�ɃI�y�����h������B
				field.setStr(getRegisterName(regNo));
				if(flgExeMode){
					field.setOperand(reg.get(regNo));
					field.setReg(regNo);
				}
				break;
			case 1:
				//���W�X�^�Ԑ�
				//register�ɃI�y�����h�̃A�h���X������B
				field.setStr("(" + getRegisterName(regNo) + ")");
				if(flgExeMode){
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
				//�����C���N�������g
				//register�ɃI�y�����h�̃A�h���X������A���ߎ��s���register�̓��e���C���N�������g����B
				field.setStr("(" + getRegisterName(regNo) + ")+");
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
				}
				break;
			case 3:
				//�����C���N�������g�Ԑ�
				//register�ɃI�y�����h�ւ̃|�C���^�̃A�h���X������A���ߎ��s���register�̓��e��2�����C���N�������g����B
				field.setStr("*(" + getRegisterName(regNo) + ")+");
				if(flgExeMode){
					field.setOperand(getMemory2(getMemory2(reg.get(regNo))));
					field.setAddress(getMemory2(reg.get(regNo)));
					reg.add(regNo,2);
				}
				break;
			case 4:
				//�����f�N�������g
				//���ߎ��s�O��register���f�N�������g���A������I�y�����h�̃A�h���X�Ƃ��Ďg�p����B
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
				}
				field.setStr("-(" + getRegisterName(regNo) + ")");
				break;
			case 5:
				//�����f�N�������g�Ԑ�
				//���ߎ��s�O��register��2�����f�N�������g���A������I�y�����h�ւ̃|�C���^�̃A�h���X�Ƃ��Ďg�p����B
				if(flgExeMode){
					//reg.add(regNo,-4);
					reg.add(regNo,-2);
					field.setOperand(getMemory2(getMemory2(reg.get(regNo))));
					field.setAddress(getMemory2(reg.get(regNo)));
				}
				field.setStr("*-(" + getRegisterName(regNo) + ")");
				break;
			case 6:
				//�C���f�b�N�X
				//register+X���I�y�����h�̃A�h���X�BX�͂��̖��߂ɑ������[�h�B
				opcodeShort = (short)getMem();
				if(opcodeShort < 0){
					field.setStr("-" + String.format("%o",~(opcodeShort - 1)) + "(" + getRegisterName(regNo) + ")");
				}else{
					field.setStr(String.format("%o",opcodeShort) + "(" + getRegisterName(regNo) + ")");
				}
				if(flgExeMode){
					if(byteFlg){
						field.setOperand(getMemory1(reg.get(regNo) + opcodeShort));
						field.setAddress(reg.get(regNo) + opcodeShort);
					}else{
						field.setOperand(getMemory2(reg.get(regNo) + opcodeShort));
						field.setAddress(reg.get(regNo) + opcodeShort);
						
					}
				}
				break;
			case 7:
				//�C���f�b�N�X�Ԑ�
				//register+X���I�y�����h�ւ̃|�C���^�̃A�h���X�BX�͂��̖��߂ɑ������[�h�B
				opcodeShort = (short)getMem();
				if(opcodeShort < 0){
					field.setStr("*-" + String.format("%o",~(opcodeShort - 1)) + "(" + getRegisterName(regNo) + ")");
				}else{
					field.setStr("*-" + String.format("%o",opcodeShort) + "(" + getRegisterName(regNo) + ")");
				}
				if(flgExeMode){
					field.setOperand(getMemory2(getMemory2(reg.get(regNo) + opcodeShort)));
					field.setAddress(getMemory2(reg.get(regNo) + opcodeShort));
				}
				break;
			}
			break;

		case 7:
			switch(mode){
			case 0:
				//���W�X�^
				//register�ɃI�y�����h������B
				field.setStr(getRegisterName(regNo));
				if(flgExeMode){
					field.setOperand(reg.get(regNo));
					field.setReg(regNo);
				}
				break;
			case 1:
				//���W�X�^�Ԑ�
				//register�ɃI�y�����h�̃A�h���X������B
				field.setStr("(" + getRegisterName(regNo) + ")");
				if(flgExeMode){
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
				//�C�~�f�B�G�[�g
				//�I�y�����h�͖��ߓ��ɂ���B
				opcodeShort = (short)getMem();
				if(opcodeShort < 0){
					field.setStr("$" + "-" + String.format("%o",~(opcodeShort - 1)));
				}else{
					field.setStr("$" + String.format("%o",opcodeShort));
				}
				if(flgExeMode){
					field.setOperand((int)opcodeShort);
				}
				break;
			case 3:
				//���
				//�I�y�����h�̐�΃A�h���X�����ߓ��ɂ���B
				opcodeShort = (short)getMem();
				if(opcodeShort < 0){
					field.setStr("*$" + "-" + String.format("%o",~(opcodeShort - 1)));
				}else{
					field.setStr("*$" + String.format("%o",opcodeShort));
				}
				if(flgExeMode){
					field.setOperand((int)opcodeShort); //������
					field.setAddress((int)opcodeShort);
				}
				break;
			case 4:
				//�����f�N�������g
				//���ߎ��s�O��register���f�N�������g���A������I�y�����h�̃A�h���X�Ƃ��Ďg�p����B
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
				}
				field.setStr("-(" + getRegisterName(regNo) + ")");
				break;
			case 5:
				//�����f�N�������g�Ԑ�
				//���ߎ��s�O��register��2�����f�N�������g���A������I�y�����h�ւ̃|�C���^�̃A�h���X�Ƃ��Ďg�p����B
				if(flgExeMode){
					reg.add(regNo,-4);
					field.setOperand(getMemory2(getMemory2(reg.get(regNo))));
					field.setAddress(getMemory2(reg.get(regNo)));
				}
				field.setStr("*-(" + getRegisterName(regNo) + ")");
				break;
			case 6:
				//����
				//���߂ɑ������[�h�̓��e a �� PC+2 �ɉ��Z�������̂��A�h���X�Ƃ��Ďg�p����B
				opcodeShort = (short)getMem();
				tmp = opcodeShort + reg.get(7);
				
				field.setStr("0x" + String.format("%02x",tmp));
				if(flgExeMode){
					field.setOperand(getMemory2(tmp));
					field.setAddress(tmp);
				}
				break;
			case 7:
				//���ΊԐ�
				//���߂ɑ������[�h�̓��e a �� PC+2 �ɉ��Z�������̂��A�h���X�̃A�h���X�Ƃ��Ďg�p����B
				opcodeInt = (int)getMem() << 16 >>> 16;
				
				tmp = opcodeInt + reg.get(7);

				field.setStr("*$0x" + String.format("%02x",(tmp)));
				field.setOperand(getMemory2(tmp)); //TODO
				field.setAddress(getMemory2(tmp));
				break;
			}
			break;
		}	
		return field;
	}

	
	/*�j�[���j�b�N�֐�------------------------------------------*/

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
	

	/*�o�͊֐�------------------------------------------*/

	//���W�X�^�E�t���O�̏o��
	void printDebug(){
		System.out.print("\n");

		System.out.print(String.format("%04x",reg.get(0) << 16 >>> 16));
		System.out.print(" " + String.format("%04x",reg.get(1) << 16 >>> 16));
		System.out.print(" " + String.format("%04x",reg.get(2) << 16 >>> 16));
		System.out.print(" " + String.format("%04x",reg.get(3) << 16 >>> 16));
		System.out.print(" " + String.format("%04x",reg.get(4) << 16 >>> 16));
		System.out.print(" " + String.format("%04x",reg.get(5) << 16 >>> 16));
		System.out.print(" " + String.format("%04x",reg.get(6) << 16 >>> 16));

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

	//�������_���v�̏o��
	void printMemory(){
		System.out.print("\n--memory-start-------------");
		for(int m=0x14d0;m<0x14ef;m=m+2){
			if(m%16==0) System.out.print(String.format("\n%02x:",m/16));
			System.out.print(" " + String.format("%04x",getMemory2(m)));
		}
		System.out.println("\n--memory-end-------------");
	}

	

}