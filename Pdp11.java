package pdp11;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class Pdp11{

	public static void main(String[] args){
		//�I�v�V����
		boolean flgDism = false;
		boolean flgDebug = false;
		boolean flgExe = false;
		boolean flgMmr = false;

		//�����G���[
		if (args.length>1){
			for(int i=1;i<args.length;i++){
				if(!(args[i].substring(0,1).equals("-"))){
					System.out.println("�I�v�V������ݒ肷��ꍇ�́A�t�@�C���� -�I�v�V�����Ɠ��͂��Ă�������");
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
			System.out.println("�t�@�C�����͈�����w�肵�Ă�������");
			System.exit(0);
		}else{
			flgDism = true;
			flgExe = true;
		}

		//�t�@�C�����擾
		File file = new File(args[0]);
		Path fileName = file.toPath();

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

		//�f�o�b�O�A�_���v�ݒ�
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

		//���s
		if(flgExe){
			vas.execute(0, vas.textSize);
		}

	}
}

/*
 * ���W�X�^�N���X
 */
class Register{

	//���W�X�^
	int[] reg;

	//�R���X�g���N�^�i�������j
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
		reg[6] = 65535; //sp�͍Ō���̃A�h���X���w��
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
 * �R���f�B�V�����R�[�h�N���X
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

	void reset(){
		n = false;
		z = false;
		v = false;
		c = false;
	}
	
	void set(boolean boolN,boolean boolZ,boolean boolV,boolean boolC){
		n= boolN;
		z= boolZ;
		v= boolV;
		c= boolC;
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

	//���[�h�i�t�A�Z���u��=0,���s=1�j
	int exeFlg;

	//���[�h�i���s=0,�f�o�b�O=1�j
	int dbgFlg;
	int mmrFlg;

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

	//8�i���ɕϊ��������߂̔C�ӂ̉ӏ����擾
	int getOctal(int dec,int index){
		int val = Integer.parseInt(String.format("%06o",dec).substring(index, index+1));
		return val;
	}

	//ASCII�R�[�h�ɕϊ������f�[�^�̔C�ӂ̉ӏ����擾
	char getChar(int dec,int index){
		char val = (char)Integer.parseInt(String.format("%02x",dec),16);
		System.out.print(val);
		return val;
	}

	//��������̃f�[�^���擾���āAPC+2����
	int getMem(){
		int opcode = getMemory2(reg.get(7));

		//�t�A�Z���u���̏ꍇ�͏o��
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

	//�w��ӏ��̃�������1byte�擾
	int getMemory1(int start){
		return mem[start];
	}

	//�w��ӏ��̃��������X�V
	void setMemory2(int add,int src){
		mem[add] = (byte)src;
		mem[add+1] = (byte)(src >> 8);
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

		//���s�t���O=0
		exeFlg = 0;

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
	public void reset(int debugFlg,int memoryFlg){
		//�f�o�b�O�t���O
		dbgFlg = debugFlg;
		mmrFlg = memoryFlg;

		//���W�X�^������
		reg.reset();

		//�R���f�B�V�����R�[�h������
		cc.reset();
	}

	//�C���^�v���^
	public void execute(int start, int end){

		//���s�t���O=1
		exeFlg = 1;

		reg.set(7,start);	

		for(reg.set(7,start);reg.get(7)<end;){

			//debug�p
			if(dbgFlg == 1){
				printDebug();
			}

			FieldDto srcObj;
			FieldDto dstObj;

			int tmp = 0;

			//���ߎ擾
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

				cc.set((tmp >>> 15)>0, tmp==0, cc.v, cc.c);

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

				cc.set((tmp >>> 15)>0, tmp==0, cc.v, cc.c);
				
				break;
			case TSTB:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5),1);
				cc.set((dstObj.operand >>> 15)>0, dstObj.operand==0, false, false);

				break;
			case TST:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				cc.set((dstObj.operand >>> 15)>0, dstObj.operand==0, false, false);
				
				break;
			case MOVB:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3),1);
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5),1);

				if(srcObj.flgRegister){
					if(dstObj.flgRegister){
						tmp = reg.get(srcObj.register) << 24;
						tmp = tmp >> 24;
						reg.set(dstObj.register, tmp);
					}else if(dstObj.flgAddress){
						tmp = reg.get(srcObj.register);
						setMemory2(dstObj.address, tmp);
					}
				}else if(srcObj.flgAddress){
					if(dstObj.flgRegister){
						tmp = getMemory2(srcObj.address) << 24;
						tmp = tmp >> 24;
						reg.set(dstObj.register, tmp);
					}else if(dstObj.flgAddress){
						tmp = getMemory2(srcObj.address);
						setMemory2(dstObj.address, tmp);
					}
				}else{
					if(dstObj.flgRegister){
						tmp = srcObj.operand << 24;
						tmp = tmp >> 24;
						reg.set(dstObj.register, tmp);
					}else if(dstObj.flgAddress){
						tmp = srcObj.operand;
						setMemory2(dstObj.address, tmp);
					}
				}

				cc.set((tmp >>> 15)>0, tmp==0, false, cc.c);
				
				break;
			case MOV:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));

				cc.set(srcObj.operand<0, srcObj.operand==0, false, cc.c);

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
			case CMPB:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3),1);
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5),1);
				tmp = srcObj.operand - dstObj.operand;

				cc.set((tmp >>> 15)>0, tmp==0, tmp>=0x10000, (srcObj.operand + (~(dstObj.operand << 16) >>> 16) + 1) < Math.pow(2.0, 16.0));

				break;
			case CMP:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				tmp = srcObj.operand - dstObj.operand;
				
				cc.set((tmp >>> 15)>0, tmp==0, tmp>=0x10000, (srcObj.operand + (~(dstObj.operand << 16) >>> 16) + 1) < Math.pow(2.0, 16.0));

				break;
			case ADD:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				tmp = srcObj.operand + dstObj.operand;
				
				boolean addC = false;
				if((dstObj.operand >>> 15) == (srcObj.operand >>> 15)){
					if((dstObj.operand >>> 15) != (tmp >>> 15)){
						addC = true;
					}
				}
				
				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else{
					setMemory2(dstObj.address, tmp);
				}				
				
				cc.set((tmp >>> 15)>0, tmp==0, addC , (srcObj.operand + (~(dstObj.operand << 16) >>> 16) + 1) < Math.pow(2.0, 16.0));

				break;
			case NEG:
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				tmp = (~(dstObj.operand << 16) >>> 16) + 1;
				
				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else if(dstObj.flgAddress){
					setMemory2(dstObj.address, tmp);
				}else{
					setMemory2(dstObj.address, tmp);
				}				

				cc.set((tmp >>> 15)>0, tmp==0, tmp>=100000, tmp!=0);

				break;
			case SOB:
				reg.set(getOctal(opcode,3),reg.get(getOctal(opcode,3)) - 1);
				if(reg.get(getOctal(opcode,3)) != 0){
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
			case BIC:
				srcObj = getField(getOctal(opcode,2),getOctal(opcode,3));
				dstObj = getField(getOctal(opcode,4),getOctal(opcode,5));

				tmp = ~(srcObj.operand) & dstObj.operand;

				if(dstObj.flgRegister){
					reg.set(dstObj.register, tmp);
				}else if(dstObj.flgAddress){
					setMemory2(dstObj.address, tmp);
				}else{
					setMemory2(dstObj.address, tmp);
				}

				cc.set(tmp>0xFF+1, tmp==0, false, cc.c);
	
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

				cc.set((tmp >>> 15)>0, tmp==0, tmp>=0x10000, (srcObj.operand + ~dstObj.operand + 1) < Math.pow(2.0, 16.0));
	
				break;
			case DIV: //��ŏ���
				int divR1 = reg.get(getOctal(opcode,3)) << 16;
				int divR2 = reg.get(getOctal(opcode,3)+1);
				
				int divValue = divR1 + divR2;
				
				srcObj = getField(getOctal(opcode,4),getOctal(opcode,5));
				
				reg.set(getOctal(opcode,3), divValue / srcObj.operand);
				reg.set(getOctal(opcode,3)+1, divValue % srcObj.operand);

				cc.set((reg.get(getOctal(opcode,3)) >>> 15)>0, 
						reg.get(getOctal(opcode,3))==0, 
						srcObj.operand==0,
						srcObj.operand==0);
				
				break;
			case MUL: //��ŏ���
				break;
			case ASHC: //��ŏ���
				break;
			case SETD:
				break;
			case WORD:
				break;
			}

			//debug�p
			if(dbgFlg == 1){
				printDebug();
			}

			//�������_���v�p
			if(mmrFlg == 1){
				printMemory();
			}
		}
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
					}
					break;
				case 1:
					mnemonic = Mnemonic.MOV;
					break;
				case 2:
					mnemonic = Mnemonic.CMP;
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
					case 5:
						switch(getOctal(opcode,3)){
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

			if(mnemonic == null){
				mnemonic = Mnemonic.WORD;
			}

		return mnemonic;
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
	FieldDto getField(int first, int second){
		FieldDto field = getField(first, second, 0);
		return field;
	}

	//�t�B�[���h�擾�idst,src�j
	FieldDto getField(int first, int second, int flgByte){

		//�Ԃ�l
		FieldDto field = new FieldDto();

		//���[�N
		short opcodeShort;
		int tmp;

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
				//���W�X�^
				//register�ɃI�y�����h������B
				field.setStr(getRegisterName(second));
				field.setOperand(reg.get(second));
				field.setReg(second);
				break;
			case 1:
				//���W�X�^�Ԑ�
				//register�ɃI�y�����h�̃A�h���X������B
				field.setStr("(" + getRegisterName(second) + ")");
				if(exeFlg == 1){
					field.setOperand(getMemory2(reg.get(second)));
					field.setAddress(reg.get(second));
				}
				break;
			case 2:
				//�����C���N�������g
				//register�ɃI�y�����h�̃A�h���X������A���ߎ��s���register�̓��e���C���N�������g����B
				field.setStr("(" + getRegisterName(second) + ")+");
				if(exeFlg == 1){
					field.setOperand(getMemory2(reg.get(second)));
					field.setAddress(reg.get(second));
					if(flgByte == 1){
						reg.add(second,1);
					}else{
						reg.add(second,2);
					}
				}
				break;
			case 3:
				//�����C���N�������g�Ԑ�
				//register�ɃI�y�����h�ւ̃|�C���^�̃A�h���X������A���ߎ��s���register�̓��e��2�����C���N�������g����B
				field.setStr("*(" + getRegisterName(second) + ")+");
				if(exeFlg == 1){
					field.setOperand(getMemory2(getMemory2(reg.get(second))));
					field.setAddress(getMemory2(reg.get(second)));
					reg.add(second,4);
				}
				break;
			case 4:
				//�����f�N�������g
				//���ߎ��s�O��register���f�N�������g���A������I�y�����h�̃A�h���X�Ƃ��Ďg�p����B
				if(exeFlg == 1){
					if(flgByte == 1){
						reg.add(second,-1);
					}else{
						reg.add(second,-2);
					}
					field.setOperand(getMemory2(reg.get(second)));
					field.setAddress(reg.get(second));
				}
				field.setStr("-(" + getRegisterName(second) + ")");
				break;
			case 5:
				//�����f�N�������g�Ԑ�
				//���ߎ��s�O��register��2�����f�N�������g���A������I�y�����h�ւ̃|�C���^�̃A�h���X�Ƃ��Ďg�p����B
				if(exeFlg == 1){
					reg.add(second,-4);
					field.setOperand(getMemory2(getMemory2(reg.get(second))));
					field.setAddress(getMemory2(reg.get(second)));
				}
				field.setStr("*-(" + getRegisterName(second) + ")");
				break;
			case 6:
				//�C���f�b�N�X
				//register+X���I�y�����h�̃A�h���X�BX�͂��̖��߂ɑ������[�h�B
				opcodeShort = (short)getMem();
				if(opcodeShort < 0){
					field.setStr("-" + String.format("%o",~(opcodeShort - 1)) + "(" + getRegisterName(second) + ")");
				}else{
					field.setStr(String.format("%o",opcodeShort) + "(" + getRegisterName(second) + ")");
				}
				if(exeFlg == 1){
					field.setOperand(getMemory2(reg.get(second) + opcodeShort));
					field.setAddress(reg.get(second) + opcodeShort);
				}
				break;
			case 7:
				//�C���f�b�N�X�Ԑ�
				//register+X���I�y�����h�ւ̃|�C���^�̃A�h���X�BX�͂��̖��߂ɑ������[�h�B
				opcodeShort = (short)getMem();
				if(opcodeShort < 0){
					field.setStr("*-" + String.format("%o",~(opcodeShort - 1)) + "(" + getRegisterName(second) + ")");
				}else{
					field.setStr("*-" + String.format("%o",opcodeShort) + "(" + getRegisterName(second) + ")");
				}
				if(exeFlg == 1){
					field.setOperand(getMemory2(getMemory2(reg.get(second) + opcodeShort)));
					field.setAddress(getMemory2(reg.get(second) + opcodeShort));
				}
				break;
			}
			break;

		case 7:
			switch(first){
			case 2:
				//�C�~�f�B�G�[�g
				//�I�y�����h�͖��ߓ��ɂ���B
				opcodeShort = (short)getMem();
				if(opcodeShort < 0){
					field.setStr("$" + "-" + String.format("%o",~(opcodeShort - 1)));
				}else{
					field.setStr("$" + String.format("%o",opcodeShort));
				}
				if(exeFlg == 1){
					field.setOperand((int)opcodeShort); //������Ƃ��₵���H
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
				if(exeFlg == 1){
					field.setOperand((int)opcodeShort); //������Ƃ��₵���H
					field.setAddress((int)opcodeShort);
				}
				break;
			case 6:
				//����
				//���߂ɑ������[�h�̓��e a �� PC+2 �ɉ��Z�������̂��A�h���X�Ƃ��Ďg�p����B
				opcodeShort = (short)getMem();
				tmp = opcodeShort + reg.get(7);
				
				field.setStr("0x" + String.format("%02x",tmp));
				if(exeFlg == 1){
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
				field.setOperand(getMemory2(tmp)); //������Ƃ��₵���H
				field.setAddress(getMemory2(tmp));
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
	RTT, RTS, JMP, JSR, CLR, TST, TSTB, MOV, MOVB, CMP, CMPB,
	INC, DEC, SUB, ADD, SOB,
	BR, BHI, BNE, BEQ, BCC, BGT, BGE, BIC, BLE,
	NEG,
	DIV, ASHC, MUL,
	SETD, SYS, WORD
};

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