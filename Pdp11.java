package pdp11;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.Stack;

public class Pdp11{

	public static void main(String[] args){
		//�I�v�V����
		boolean flgDism = false;
		boolean flgDebug = false;
		boolean flgExe = false;
		boolean flgMmr = false;

		//�I�v�V�����ݒ�
		int i = 0;
		while(true){
			if(!(args[i].substring(0,1).equals("-"))){
				break;
			}
			//���W�X�^�E�t���O
			if(args[i].equals("-v")){
				flgDebug = true;
			}
			//�t�A�Z���u��
			if(args[i].equals("-d")){
				flgDism = true;
			}
			//���s
			if(args[i].equals("-e")){
				flgExe = true;
			}
			//�������_���v
			if(args[i].equals("-m")){
				flgMmr = true;
			}
			i++;
		}
		
		//�I�v�V�����w�肪�Ȃ���΋t�A�Z���u���Ǝ��s
		if(!flgDebug && !flgDism && !flgExe && !flgMmr){
			flgDism = true;
			flgExe = true;
		}
		
		//�t�@�C�����ݒ�
		File file = new File(args[i]);
		Path fileName = file.toPath();
		
		//�����ݒ�
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

		//�t�@�C�����e�擾
		byte[] bf = null;
		try {
	        bf = java.nio.file.Files.readAllBytes(fileName);
		} catch (IOException e) {
			e.printStackTrace();
		}

		//���z�������Ƀ��[�h
		VirtualAddressSpace vas = new VirtualAddressSpace(bf);

		//�t�A�Z���u��
		if(flgDism){
			vas.disassemble(0,vas.textSize);
		}

		//���s�O�ݒ�
		if(flgDebug){
			if(flgMmr){
				vas.reset(true, true, argStack);
			}else{
				vas.reset(true, false, argStack);
			}
		}else{
			if(flgMmr){
				vas.reset(false, true, argStack);
			}else{
				vas.reset(false, false, argStack);
			}
		}

		//���s
		if(flgExe){
			vas.execute(0, vas.textSize);
		}
	}
}


/*
 * ���z�A�h���X��ԃN���X
 */
class VirtualAddressSpace{

	//���z������
	byte[] mem;
	//�������T�C�Y
	int memorySize = 65536;

	//���W�X�^
	Register reg;

	//�R���f�B�V�����R�[�h
	ConditionCode cc;

	//�o�͗p���[�e�B���e�B
	int strnum;

	//���s���[�h
	boolean exeFlg;
	//���W�X�^�E�t���O���[�h
	boolean dbgFlg;
	//�������_���v���[�h
	boolean mmrFlg;

	//�̈�̑傫��
	int headerSize = 16;
	int textSize;
	int dataSize;
	int bssSize;

	//�}�W�b�N�i���o�[
	int magicNo;

	//�R���X�g���N�^
	VirtualAddressSpace(byte[] bf){

		//�}�W�b�N�i���o�[���擾
		magicNo = ((int)bf[1] & 0xFF)|(((int)bf[2] & 0xFF) << 8);

		//�T�C�Y���擾
		textSize = ((int)bf[2] & 0xFF)|(((int)bf[3] & 0xFF) << 8);
		dataSize = ((int)bf[4] & 0xFF)|(((int)bf[5] & 0xFF) << 8);
		bssSize = ((int)bf[6] & 0xFF)|(((int)bf[7] & 0xFF) << 8);

		//������������
		mem = new byte[memorySize];
		int i;
		int cnt = 0;

		//�e�L�X�g�̈�ǂݍ���
		for(i=headerSize;i<headerSize+textSize;i++){
			mem[cnt] = bf[i];
			cnt++;
		}

		//�f�[�^�̈�ǂݍ���
		for(;i<headerSize+textSize+dataSize;i++){
			mem[cnt] = bf[i];
			cnt++;
		}

		//���̑��̃�����������
		for(;cnt<memorySize;cnt++){
			mem[cnt] = 0;
		}

		//���W�X�^������
		reg = new Register();

		//�R���f�B�V�����R�[�h������
		cc = new ConditionCode();
	}

	//2�o�C�g�P�ʂŃ��g���G���f�B�A���𔽓]����10�i���Ŏ擾
	int getMemory2(int start){
		return (int) ((int)(mem[start]) & 0xFF)|( (int)((mem[start+1] & 0xFF) << 8));
	}

	//1�o�C�g�P�ʂŎw��ӏ��̃��������擾
	int getMemory1(int start){
		return mem[start];
	}

	//2�o�C�g�P�ʂŎw��ӏ��̃��������X�V
	void setMemory2(int add,int src){
		mem[add] = (byte)src;
		mem[add+1] = (byte)(src >> 8);
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
	
	//��������̃f�[�^���擾���āAPC+2����
	int getMem(){
		int opcode = getMemory2(reg.get(7));

		//�t�A�Z���u���̏ꍇ�͏o��
		if(exeFlg){
			if(dbgFlg) printOpcode(opcode);
		}else{
			printOpcode(opcode);
			strnum++;
		}

		//PC+2
		reg.add(7,2);

		return opcode;
	}

	//�X�^�b�N�ς�
	void pushStack(int n){
		reg.add(6,-2);
		setMemory2(reg.get(6),n);
	}

	//�w�肵�����߂��o��
	void printOpcode(int opcode){
		System.out.print(String.format("%04x", opcode));
		System.out.print(" ");
	}

	//�t�A�Z���u��
	void disassemble(int start, int end){

		//���s���[�h�I�t
		exeFlg = false;

		//���W�X�^������
		reg.reset();

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
			case DEC:
				mnemonic = "dec";
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
			case BIC:
				mnemonic = "bic";
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

			//�o��
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

	//�C���^�v���^���s�O�ݒ�
	public void reset(boolean debugFlg,boolean memoryFlg, Stack<Byte> args){
		//�f�o�b�O�t���O
		dbgFlg = debugFlg;
		mmrFlg = memoryFlg;

		//���W�X�^������
		reg.reset();

		//�R���f�B�V�����R�[�h������
		cc.reset();

		//�����ݒ�
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

	//�C���^�v���^
	public void execute(int start, int end){

		//���s���[�h�I��
		exeFlg = true;
		
		//PC��������
		reg.set(7,start);	

		for(reg.set(7,start);reg.get(7)<end;){

			//���W�X�^�E�t���O�o��
			if(dbgFlg) printDebug();
			//�������_���v�o��
			if(mmrFlg) printMemory();

			//���[�N
			FieldDto srcObj;
			FieldDto dstObj;
			int tmp = 0;

			//���ߎ擾
			int opcode = getMem();

			//�j�[���j�b�N�擾
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
			case JSR:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				pushStack(reg.get(getOctal(opcode,3)));
				reg.set(getOctal(opcode,3),reg.get(7));
				reg.set(7, dstObj.address);

				cc.set(cc.n, cc.z, cc.v, cc.c);

				break;
			case CLR:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				if(dstObj.flgRegister){
					reg.set(dstObj.register, 0);
				}else{
					setMemory2(dstObj.address,0);
				}
				
				cc.reset();
				
				break;
			case CLRB: //�o�C�g���߂Ƃ́H
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				if(dstObj.flgRegister){
					reg.set(dstObj.register, 0);
				}else{
					setMemory2(dstObj.address,0);
				}
				
				cc.reset();
				
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

				cc.set((tmp >> 15)>0, tmp==0, cc.v, cc.c);

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

				cc.set((tmp >> 15)>0, tmp==0, cc.v, cc.c);
				
				break;
			case TSTB:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5), true);
				cc.set((dstObj.operand >> 15)>0, dstObj.operand==0, false, false);

				break;
			case TST:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				cc.set((dstObj.operand >> 15)>0, dstObj.operand==0, false, false);
				
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
						setMemory2(dstObj.address, tmp);
					}
				}else if(srcObj.flgAddress){
					if(dstObj.flgRegister){
						//���[�h0�̏ꍇ�A�����g�����s��
						tmp = getMemory2(srcObj.address) << 24;
						tmp = tmp >> 24;
						reg.set(dstObj.register, tmp);
					}else if(dstObj.flgAddress){
						tmp = getMemory2(srcObj.address);
						setMemory2(dstObj.address, tmp);
					}
				}else{
					if(dstObj.flgRegister){
						//���[�h0�̏ꍇ�A�����g�����s��
						tmp = srcObj.operand << 24;
						tmp = tmp >> 24;
						reg.set(dstObj.register, tmp);
					}else if(dstObj.flgAddress){
						tmp = srcObj.operand;
						setMemory2(dstObj.address, tmp);
					}
				}

				cc.set((tmp >> 15)>0, tmp==0, false, cc.c);
				
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

				cc.set((srcObj.operand >> 15)>0, srcObj.operand==0, false, cc.c);

				break;
			case CMPB:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3), true);
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5), true);
				tmp = srcObj.operand - dstObj.operand;

				cc.set((tmp >> 15)>0, 
						tmp==0, 
						getAddOverflow(srcObj.operand, dstObj.operand, tmp), 
						getSubBorrow(srcObj.operand, dstObj.operand, tmp));

				break;
			case CMP:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				tmp = srcObj.operand - dstObj.operand;
				
				cc.set((tmp >> 15)>0, 
						tmp==0, 
						getAddOverflow(srcObj.operand, dstObj.operand, tmp), 
						getSubBorrow(srcObj.operand, dstObj.operand, tmp));

				break;
			case BIT:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				tmp = srcObj.operand & dstObj.operand;
				
				cc.set(false, //��ŏ��� 
						tmp==0, 
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

				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else{
					setMemory2(dstObj.address, tmp);
				}
				
				cc.set((tmp >> 15)>0, 
						tmp==0, 
						getSubOverflow(srcObj.operand, dstObj.operand, tmp), 
						getSubBorrow(srcObj.operand, dstObj.operand, tmp));
	
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
			case BIC:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));

				tmp = ~(srcObj.operand) & dstObj.operand;

				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else{
					setMemory2(dstObj.address, tmp);
				}

				cc.set(tmp>0xFF, tmp==0, false, cc.c);

				break;
			case SYS:
				
				int textMemNum;
				int operand2;

				switch(getDex(getOctal(opcode,4),getOctal(opcode,5))){
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
					textMemNum = getMem();
					operand2 = getMem();

					for(int i=0;i<operand2;i++){
						printChar(getMemory1(textMemNum));
						textMemNum++;
					}
					break;
				case 5:
					textMemNum = getMem();
					operand2 = getMem();
					
					//stringbuffer�Ńt�@�C���������open����
					
					System.out.println("open:" + getMemory2(textMemNum));
					break;
				case 41:
					break;
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
						srcObj.operand==0, //��ŏ���
						srcObj.operand==0);
				
				break;
			case MUL: //��ŏ���
				/*
				int mulR = reg.get(getOctal(opcode,3));
				srcObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				
				if(getOctal(opcode,3)%2 ==0){
					reg.set(getOctal(opcode,3), (mulR * srcObj.operand >> 16) << 16);
					reg.set(getOctal(opcode,3)+1, (mulR * srcObj.operand << 16) >>> 16);
				}else{
					reg.set(getOctal(opcode,3), (mulR * srcObj.operand << 16) >>> 16);
				}
				cc.set((mulR * srcObj.operand >>> 15)>0, 
						mulR * srcObj.operand==0, 
						false,
						false);
				*/
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
				cc.set((reg.get(getOctal(opcode,3)) >> 15)>0,  //�v����
						reg.get(getOctal(opcode,3))==0, 
						((ashReg << 16 ) >>> 31) != ((reg.get(getOctal(opcode,3)) << 16) >>> 31), //�v����
						false); //��ŏ���
				
				break;
			case ASHC: //��ŏ���
				break;
			case SETD:
				break;
			case WORD:
				break;
			}

		}
	}

	//���Z�I�[�o�[�t���[����
	boolean getAddOverflow(int src, int dst, int val){
		boolean addV = false;
		if((dst >> 15) == (src >> 15)){
			if((dst >> 15) != (val >> 15)){
				addV = true;
			}
		}
		return addV;
	}

	//���Z�I�[�o�[�t���[����
	boolean getSubOverflow(int src, int dst, int val){
		boolean subV = false;
		if((dst >> 15) != (src >> 15)){
			if((src >> 15) == (val >> 15)){
				subV = true;
			}
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
	
	//���Z�{���[����
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
		FieldDto field = getField(mode, regNo, false);
		return field;
	}

	//�t�B�[���h�擾�idst,src�j
	FieldDto getField(int mode, int regNo, boolean byteFlg){

		//�Ԃ�l
		FieldDto field = new FieldDto();

		//���[�N
		short opcodeShort;
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
				if(exeFlg){
					field.setOperand(reg.get(regNo));
					field.setReg(regNo);
				}
				break;
			case 1:
				//���W�X�^�Ԑ�
				//register�ɃI�y�����h�̃A�h���X������B
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
				//�����C���N�������g
				//register�ɃI�y�����h�̃A�h���X������A���ߎ��s���register�̓��e���C���N�������g����B
				field.setStr("(" + getRegisterName(regNo) + ")+");
				if(exeFlg){
					if(byteFlg){
						field.setOperand(getMemory1(reg.get(regNo)));
						field.setAddress(reg.get(regNo));
						reg.add(regNo,1);
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
				if(exeFlg){
					field.setOperand(getMemory2(getMemory2(reg.get(regNo))));
					field.setAddress(getMemory2(reg.get(regNo)));
					reg.add(regNo,4);
				}
				break;
			case 4:
				//�����f�N�������g
				//���ߎ��s�O��register���f�N�������g���A������I�y�����h�̃A�h���X�Ƃ��Ďg�p����B
				if(exeFlg){
					if(byteFlg){
						reg.add(regNo,-1);
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
				if(exeFlg){
					reg.add(regNo,-4);
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
				if(exeFlg){
					field.setOperand(getMemory2(reg.get(regNo) + opcodeShort));
					field.setAddress(reg.get(regNo) + opcodeShort);
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
				if(exeFlg){
					field.setOperand(getMemory2(getMemory2(reg.get(regNo) + opcodeShort)));
					field.setAddress(getMemory2(reg.get(regNo) + opcodeShort));
				}
				break;
			}
			break;

		case 7:
			switch(mode){
			case 2:
				//�C�~�f�B�G�[�g
				//�I�y�����h�͖��ߓ��ɂ���B
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
				//���
				//�I�y�����h�̐�΃A�h���X�����ߓ��ɂ���B
				opcodeShort = (short)getMem();
				if(opcodeShort < 0){
					field.setStr("*$" + "-" + String.format("%o",~(opcodeShort - 1)));
				}else{
					field.setStr("*$" + String.format("%o",opcodeShort));
				}
				if(exeFlg){
					field.setOperand((int)opcodeShort); //������
					field.setAddress((int)opcodeShort);
				}
				break;
			case 6:
				//����
				//���߂ɑ������[�h�̓��e a �� PC+2 �ɉ��Z�������̂��A�h���X�Ƃ��Ďg�p����B
				opcodeShort = (short)getMem();
				tmp = opcodeShort + reg.get(7);
				
				field.setStr("0x" + String.format("%02x",tmp));
				if(exeFlg){
					field.setOperand(getMemory2(tmp));
					field.setAddress(tmp);
				}
				break;
			case 7:
				//���ΊԐ�
				//���߂ɑ������[�h�̓��e a �� PC+2 �ɉ��Z�������̂��A�h���X�̃A�h���X�Ƃ��Ďg�p����B
				opcodeShort = (short)getMem();
				tmp = opcodeShort + reg.get(7);

				field.setStr("*$0x" + String.format("%02x",(tmp)));
				field.setOperand(getMemory2(tmp)); //������
				field.setAddress(getMemory2(tmp));
				break;
			}
			break;
		}	
		return field;
	}

	//�j�[���j�b�N�擾
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
					case 7:
						mnemonic = Mnemonic.TST;
						break;
					}
					break;
				case 6:
					switch(getOctal(opcode,3)){
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

	//���W�X�^�E�t���O�̏o��
	void printDebug(){
		System.out.print("\n");

		System.out.print(" " + String.format("%04x",reg.get(0)));
		System.out.print(" " + String.format("%04x",reg.get(1)));
		System.out.print(" " + String.format("%04x",reg.get(2)));
		System.out.print(" " + String.format("%04x",reg.get(3)));
		System.out.print(" " + String.format("%04x",reg.get(4)));
		System.out.print(" " + String.format("%04x",reg.get(5)));
		System.out.print(" " + String.format("%04x",reg.get(6)));
		System.out.print(" " + String.format("%04x",reg.get(7)));

		if(cc.n){
			System.out.print(" n");
		}else{
			System.out.print(" -");
		}
		if(cc.z){
			System.out.print("z");
		}else{
			System.out.print("-");
		}
		if(cc.v){
			System.out.print("v");
		}else{
			System.out.print("-");
		}
		if(cc.c){
			System.out.print("c ");
		}else{
			System.out.print("- ");
		}
	}

	//�������_���v�̏o��
	void printMemory(){
		System.out.print("\n--memory-start-------------");
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

/*
 * �j�[���j�b�NENUM
 */
enum Mnemonic { 
	RTT, RTS, JMP, JSR, CLR, CLRB, TST, TSTB, MOV, MOVB, CMP, CMPB, BIT,
	INC, DEC, SUB, ADD, SOB, SXT,
	BR, BHI, BNE, BEQ, BCC, BGT, BGE, BIC, BLE, BLOS, BCS,
	NEG, ASL,
	DIV, ASH, ASHC, MUL,
	SETD, SYS, WORD
};

/*
 * �t�B�[���hDto
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
 * ���W�X�^�N���X
 * R1-R5:�ėp���W�X�^
 * R6:�X�^�b�N�|�C���^SP
 * R7:�v���O�����J�E���^PC
 */
class Register{

	//���W�X�^
	int[] reg;

	//�R���X�g���N�^�i�������j
	Register(){
		reg = new int[8];
		reset();
	}

	//���W�X�^������
	void reset(){
		reg[0] = 0;
		reg[1] = 0;
		reg[2] = 0;
		reg[3] = 0;
		reg[4] = 0;
		reg[5] = 0;
		reg[6] = 65536; //sp�͍Ō���̃A�h���X���w��
		reg[7] = 0;
	}

	//���W�X�^���㏑��
	void set(int regNo,int val){
		reg[regNo] = val;
	}

	//���W�X�^�ɉ��Z
	void add(int regNo,int val){
		reg[regNo] = reg[regNo] + val;
	}

	//���W�X�^���擾
	int get(int regNo){
		return reg[regNo];
	}
}

/*
 * �R���f�B�V�����R�[�h�N���X
 * Z:�[���̏ꍇ
 * N:���̏ꍇ
 * C:MSB(�ŏ�ʃr�b�g)����L�����������AMSB/LSB(�ŉ��ʃr�b�g)����1���V�t�g���ꂽ�ꍇ
 * V:�I�[�o�[�t���[�����������ꍇ
 */
class ConditionCode{

	boolean n;
	boolean z;
	boolean v;
	boolean c;

	//�R���X�g���N�^�i�������j
	ConditionCode(){
		reset();
	}

	//�R���f�B�V�����R�[�h������
	void reset(){
		n = false;
		z = false;
		v = false;
		c = false;
	}
	
	//�R���f�B�V�����R�[�h�ݒ�
	void set(boolean boolN,boolean boolZ,boolean boolV,boolean boolC){
		n= boolN;
		z= boolZ;
		v= boolV;
		c= boolC;
	}
}