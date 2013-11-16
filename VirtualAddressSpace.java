package pdp11;


public class VirtualAddressSpace implements Cloneable{

	//仮想メモリ
	byte[] mem;
	//メモリサイズ
	int memorySize = 65536;

	//領域の大きさ
	int headerSize = 16;
	int textSize;
	int dataSize;
	int bssSize;

	//マジックナンバー
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

	
	//コンストラクタ
	VirtualAddressSpace(byte[] bf){

		//マジックナンバーを取得
		magicNo = ((int)bf[1] & 0xFF)|(((int)bf[2] & 0xFF) << 8);

		//サイズを取得
		textSize = ((int)bf[2] & 0xFF)|(((int)bf[3] & 0xFF) << 8);
		dataSize = ((int)bf[4] & 0xFF)|(((int)bf[5] & 0xFF) << 8);
		bssSize = ((int)bf[6] & 0xFF)|(((int)bf[7] & 0xFF) << 8);

		//メモリ初期化
		mem = new byte[memorySize];
		int i;
		int cnt = 0;

		//テキスト領域読み込み
		for(i=headerSize;i<headerSize+textSize;i++){
			mem[cnt] = bf[i];
			cnt++;
		}

		//データ領域読み込み
		for(;i<headerSize+textSize+dataSize;i++){
			mem[cnt] = bf[i];
			cnt++;
		}

		//その他のメモリ初期化
		for(;cnt<memorySize;cnt++){
			mem[cnt] = 0;
		}

	}

	public VirtualAddressSpace() {
		// TODO Auto-generated constructor stub
	}

	//2バイト単位でリトルエンディアンを反転して10進数で取得
	int getMemory2(int start){
		return (int) ((int)(mem[start]) & 0xFF)|( (int)((mem[start+1] & 0xFF) << 8));
	}

	//1バイト単位で指定箇所のメモリを取得
	int getMemory1(int start){
		return mem[start];
	}

	//2バイト単位で指定箇所のメモリを更新
	void setMemory2(int add,int src){
		mem[add] = (byte)src;
		mem[add+1] = (byte)(src >> 8);
	}

	//1バイト単位で指定箇所のメモリを更新
	void setMemory1(int add,int src){
		mem[add] = (byte)src;
	}

	


}
