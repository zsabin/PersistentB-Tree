import java.io.*;

/**
 * A Random Access File composed of blocks of a fixed length
 */
public class FLRAF extends java.io.RandomAccessFile
{
	private int blockSize;
	private String fileName;

   /**
	* Creates a Fixed Length Random Access File
	*
	* @param	name	the name of the file
	* @param	mode	the access mode
	* @param	blockSize	the number of bytes per block
	*/
	public FLRAF(String name, String mode, int blockSize) throws FileNotFoundException
	{
		super(name, mode);
		this.fileName = name;
		this.blockSize = blockSize;
	}

   /**
	* Reads the block of bytes found at the specified index into an array of bytes
	*
	* @param	block	the index of the block to read from
	* @param	bytes	the array to read the bytes into
	*
	* @return			the number of bytes read or -1 if the end of the file was reached
	*/
	public int read(int block, byte[] bytes) throws IOException
	{
		seek(block * blockSize);
		return super.read(bytes);
	}

   /**
	* Reads the block of bytes found at the specified index into an array of bytes
	*
	* @param	block	the index of the block to write to
	* @param	bytes	an array of bytes to be written to the file
	*/
	public void write(int block, byte[] bytes) throws IOException
	{
		seek(block * blockSize);
		super.write(bytes);
	}

	public int getBlockSize()
	{
		return this.blockSize;
	}

	public String getFileName()
	{
		return this.fileName;
	}	
}
