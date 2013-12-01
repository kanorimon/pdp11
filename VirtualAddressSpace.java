package pdp11;

public class VirtualAddressSpace implements Cloneable{
	byte[] mem; //���z������
	int memorySize = 65536; //�������T�C�Y
	int magicNo; //�}�W�b�N�i���o�[

	//�̈�̑傫��
	int headerSize = 16;
	int textSize;
	int dataSize;
	int bssSize;
	
	public Object clone() {
		VirtualAddressSpace cloneVas = null;
		try {
			cloneVas = (VirtualAddressSpace)super.clone();
			cloneVas.mem = this.mem.clone();
		} catch (CloneNotSupportedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return cloneVas;  
	}

	
	//�R���X�g���N�^
	VirtualAddressSpace(byte[] bf){

		//�}�W�b�N�i���o�[���擾
		magicNo = ((int)bf[0] & 0xFF)|(((int)bf[1] & 0xFF) << 8);

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
		
		//�}�W�b�N�i���o�[410�Ή�
		if(magicNo==0x108){
			while(true){
				if(cnt%0x2000==0) break;
				mem[cnt] = 0;
				cnt++;
			}
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
		// TODO
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
