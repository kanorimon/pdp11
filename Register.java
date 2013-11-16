package pdp11;

/*
 * レジスタクラス
 * R1-R5:汎用レジスタ
 * R6:スタックポインタSP
 * R7:プログラムカウンタPC
 */
public class Register implements Cloneable{
	int[] reg; //レジスタ

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
	
	//コンストラクタ（初期化）
	Register(){
		reg = new int[8];
		reset();
	}

	//レジスタ初期化
	void reset(){
		reg[0] = 0;
		reg[1] = 0;
		reg[2] = 0;
		reg[3] = 0;
		reg[4] = 0;
		reg[5] = 0;
		reg[6] = 65536; //spは最後尾のアドレスを指す
		reg[7] = 0;
	}

	//レジスタを上書き
	void set(int regNo,int val){
		reg[regNo] = val;
	}

	//レジスタに加算
	void add(int regNo,int val){
		if(reg[regNo]+val > 0xffff){
			reg[regNo] = (reg[regNo]+val) << 16 >>> 16;
		}else{
			reg[regNo] = reg[regNo] + val;
		}
	}

	//レジスタを取得
	int get(int regNo){
		return reg[regNo];
	}
}


/*
 * コンディションコードクラス
 * Z:ゼロの場合
 * N:負の場合
 * C:MSB(最上位ビット)からキャリが発生、MSB/LSB(最下位ビット)から1がシフトされた場合
 * V:オーバーフローが発生した場合
 */
class ConditionCode{

	boolean n;
	boolean z;
	boolean v;
	boolean c;

	//コンストラクタ（初期化）
	ConditionCode(){
		reset();
	}

	//コンディションコード初期化
	void reset(){
		n = false;
		z = false;
		v = false;
		c = false;
	}
	
	//コンディションコード設定
	void set(boolean boolN,boolean boolZ,boolean boolV,boolean boolC){
		n= boolN;
		z= boolZ;
		v= boolV;
		c= boolC;
	}
}