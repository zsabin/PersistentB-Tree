import java.util.*;
import java.nio.*;
import java.nio.charset.*;

 /**
	* A class to convert B-Tree nodes to and from bytes
	*/
public class BTreeNodeConverter
{
	public static final int LINK_SIZE = 4;
	public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
	public final int BYTES_PER_CHAR;
	public final int NULL_LINK = -1;

	public final int order;
	public final int nodeSize;
	public final Charset charset;
	public final int keySize;
	public final int keyLength;

 /**
	* Creates a new B-Tree Node converter based on the specified properties
	*
	* @param	order		the order of the B-Tree nodes
	* @param	nodeSize	the size of the BTree nodes
	*/
	public BTreeNodeConverter(int order, int nodeSize) throws Exception
	{
		this(order, nodeSize, DEFAULT_CHARSET);
	} 

 /**
	* Creates a new B-Tree Node converter based on the specified properties
	*
	* @param	order		the order of the B-Tree nodes
	* @param	nodeSize	the size of the BTree nodes
	* @param	charset		the charset to be used to convert the Btree keys to and from bytes
	*/
	public BTreeNodeConverter(int order, int nodeSize, Charset charset) throws Exception
	{
		this.order = order;
		this.nodeSize = nodeSize;
		this.charset = charset;
		
		double keySize = ( nodeSize - (order * LINK_SIZE) ) / ( (order - 1) * 1.0 );
		if (keySize != (int)keySize)
		{
			throw new Exception();
		}
		this.keySize = (int)keySize;

		this.BYTES_PER_CHAR = (int)charset.newEncoder().averageBytesPerChar();
		double keyLength = keySize / (BYTES_PER_CHAR * 1.0);
		if (keyLength != (int)keyLength)
		{
			throw new Exception();
		}
		this.keyLength = (int)keyLength;
	}

 /**
	* Creates a BTreeNode from an array of bytes
	*
	* @param	bytes		the array of bytes to be converted to a node
	* @return	 			a BTreeNode converted from the bytes
	*/
	public BTreeNode<String> readFromBytes(byte[] bytes) throws Exception
	{
		if (bytes.length != nodeSize) throw new Exception();

		ArrayList<String> keys = new ArrayList<String>();
		ArrayList<Integer> links = new ArrayList<Integer>();

		//Read in Keys
		int byteIndex = 0;
		while (byteIndex < ( (order - 1) * keySize))
		{
			byte[] keyAsBytes = Arrays.copyOfRange(bytes, byteIndex, byteIndex + keySize);
			String key = new String(keyAsBytes, charset);
			key = key.trim();
			
			if (key.length() == 0) break;

			keys.add(key);	
			byteIndex += keySize;
		}

		//Read in Links
		byteIndex = (order - 1) * keySize;
		while (byteIndex < nodeSize)
		{
			byte[] linkAsBytes = Arrays.copyOfRange(bytes, byteIndex, byteIndex + LINK_SIZE);
			int linkAsInt = ByteBuffer.wrap(linkAsBytes).getInt();

			if(linkAsInt == -1) break;
			
			links.add(linkAsInt);
			byteIndex += LINK_SIZE;
		}
		
		BTreeNode<String> node = new BTreeNode<String>(order, keys);
		node.childLinks = links;
		return node;
	}

 /**
	* Converts a BTreeNode to an array of bytes
	*
	* @param	node		the node to be converted to bytes
	* @return	 			an array of bytes
	*/
	public byte[] writeToBytes(BTreeNode<String> node) throws Exception
	{
		if (node.order != this.order)
		{
			throw new Exception();
		}

		byte[] bytes = new byte[nodeSize];

		//pad keys with spaces and convert to bytes
		String keyString = "";
		for (String key : node.keys)
		{
			key = padString(key, keyLength);
			keyString = keyString + key;
		}
		for (int i = node.keys.size(); i < node.order - 1; i++)
		{
			keyString = keyString + padString("", keyLength);
		}
		byte[] keysAsBytes = keyString.getBytes(charset);
		bytes = concatArray(bytes, keysAsBytes);
		int byteIndex = keysAsBytes.length;

		//convert links to bytes
		for (int link : node.childLinks)
		{
			byte[] linkAsBytes = ByteBuffer.allocate(LINK_SIZE).putInt(link).array();
			bytes = concatArray(bytes, linkAsBytes, byteIndex);
			byteIndex += LINK_SIZE;
		}
		
		//replace empty links with -1 and convert to bytes
		byte[] nullLink = ByteBuffer.allocate(LINK_SIZE).putInt(NULL_LINK).array();
		for (int i = node.childLinks.size(); i < node.order; i++)
		{
			bytes = concatArray(bytes, nullLink, byteIndex);
			byteIndex += LINK_SIZE;
		}

		return bytes;
	}

	private String padString(String str, int length)
	{
		while (str.length() < length)
		{
			str = str.concat(" ");
		}
		return str;
	}

	//Copies the full contents of the source array to the destination array starting the begining of the dest array
	private byte[] concatArray(byte[] dest, byte[] src)
	{
		return concatArray(dest, src, 0);
	}

	//Copies the full contents of the source array to the destination array starting at the destination index
	private byte[] concatArray(byte[] dest, byte[] src, int destIndex)
	{
		for (int i = 0; i < src.length; i++ )
		{
			dest[destIndex + i] = src[i];
		}
		return dest;
	}
}
