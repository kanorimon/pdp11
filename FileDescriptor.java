package pdp11;

import java.io.IOException;
import java.nio.file.Path;

public class FileDescriptor{
	BlockFile inode[];
	
	//コンストラクタ（初期化）
	FileDescriptor(){
		inode = new BlockFile[16];
		inode[0] = new BlockFile(System.in,true,false,false,false);
		inode[1] = new BlockFile(System.out,false,true,false,false);
		inode[2] = new BlockFile(System.err,false,false,true,false);
	}
	
	//open
	int open(int no,Object in){
		inode[no] = new BlockFile(in,false,false,false,true);
		inode[no].open();
		return no;
	}
	
	//種類を取得
	boolean isStdin(int no){
		return inode[no].stdin;
	}
	boolean isStdout(int no){
		return inode[no].stdout;
	}
	boolean isStderror(int no){
		return inode[no].stderror;
	}
	boolean isFile(int no){
		return inode[no].file;
	}
	
	//get
	Object get(int no){
		return inode[no];
	}
	
	//readByte
	byte readByte(int no){
		return inode[no].readByte();
	}
	
	//getSize
	int getSize(int no){
		return inode[no].getSize();
	}

	//getOffset
	int getOffset(int no){
		return inode[no].getOffset();
	}

	//setOffset
	int setOffset(int no,int offset){
		inode[no].setOffset(offset);
		return inode[no].getOffset();
	}
	
	//clear
	int clear(int no){
		inode[no] = null;
		return no;
	}
	
	//copy
	int copy(int to,int from){
		inode[to] = inode[from];
		return to;
	}
	
	//search
	int search(){
		for(int i=0;i<16;i++){
			if(inode[i]==null){
				return i;
			}
		}
		return 16;
	}
	
}

/*
 * ファイルクラス
 */
class BlockFile{
	Object inode;
	int offset;
	byte[] strage;
	boolean stdin;
	boolean stdout;
	boolean stderror;
	boolean file;
	
	//コンストラクタ（初期化）
	BlockFile(Object input,boolean in,boolean out,boolean error,boolean fl){
		inode = input;
		offset = 0;
		strage = null;
		stdin = in;
		stdout = out;
		stderror = error;
		file = fl;
	}
	
	void open(){
		try {
			strage = java.nio.file.Files.readAllBytes((Path)inode);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	
	int getSize(){
		return strage.length;
	}

	int getOffset(){
		return offset;
	}

	int setOffset(int in){
		offset = in;
		return offset;
	}
	
	byte readByte(){
		byte val = strage[offset];
		offset++;
		return val;
	}

}