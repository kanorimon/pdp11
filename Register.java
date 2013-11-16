package pdp11;

/*
 * ���W�X�^�N���X
 * R1-R5:�ėp���W�X�^
 * R6:�X�^�b�N�|�C���^SP
 * R7:�v���O�����J�E���^PC
 */
public class Register implements Cloneable{
	int[] reg; //���W�X�^

	public Object clone() {
		Register cloneRegister = null;
		try {
			cloneRegister = (Register)super.clone();
			cloneRegister.reg = this.reg.clone();
		} catch (CloneNotSupportedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    return cloneRegister;
	}
	
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
		if(reg[regNo]+val > 0xffff){
			reg[regNo] = (reg[regNo]+val) << 16 >>> 16;
		}else{
			reg[regNo] = reg[regNo] + val;
		}
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