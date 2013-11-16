package pdp11;


public class VirtualAddressSpace implements Cloneable{

	//���z������
	byte[] mem;
	//�������T�C�Y
	int memorySize = 65536;

	//�̈�̑傫��
	int headerSize = 16;
	int textSize;
	int dataSize;
	int bssSize;

	//�}�W�b�N�i���o�[
	int magicNo;
	
	public Object clone() {
		VirtualAddressSpace cloneVas = null;
		try {
			cloneVas = (VirtualAddressSpace)super.clone();
			cloneVas.mem = this.mem.clone();
		} catch (CloneNotSupportedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//cloneVas.reg = (Register) this.reg.clone();

		/*
		byte[] mem2 = new byte[mem.length];
		mem2 = mem.clone();
		*/
	    return cloneVas;  
	}

	
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

	}

	public VirtualAddressSpace() {
		// TODO Auto-generated constructor stub
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

	//1�o�C�g�P�ʂŎw��ӏ��̃��������X�V
	void setMemory1(int add,int src){
		mem[add] = (byte)src;
	}

	


}
