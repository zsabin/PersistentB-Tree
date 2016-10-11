import java.util.*;

/**
 * A Cache Manager class
 */
public class CacheManager
{
	private int size;
	private List<CacheElement> cache;
	private FLRAF file;
	private Stack<Integer> unallocatedBlocks;
	private int allocatedBlockIndex;

	private class CacheElement
	{
		public boolean modified;
		public int blockNumber;
		public byte[] block;

		public CacheElement(int blockNumber, byte[] block, boolean modified)
		{		
			this.blockNumber = blockNumber;
			this.block = block;
			this.modified = modified;
		}
	}
	
 /**
	* Creates a Cache Manager based on the specified properties
	*
	* @param	size	the number of blocks in the cache
	* @param	file	the FLRAF to be read from and written to
	*/
	public CacheManager(int size, FLRAF file) throws Exception
	{		
		this(size, file, new Stack<Integer>());
	}	

 /**
	* Creates a Cache Manager based on the specified properties.
	*
	* @param	size	the number of blocks in the cache
	* @param	file	the FLRAF to be read from and written to
	* @param	unallocatedBlocks 	a stack of unallocatedBlocks
	*/
	public CacheManager(int size, FLRAF file, Stack<Integer> unallocatedBlocks) throws Exception
	{		
		this.size = size;
		this.cache = new ArrayList<CacheElement>();
		this.file = file;
		this.unallocatedBlocks = unallocatedBlocks;
		this.allocatedBlockIndex = (int)(file.length() / file.getBlockSize()) - 1;
	}	

 /**
	* Returns the FLRAF associated with this Cache Manager
	*
	* @return	the FLRAF associated with this Cache Manager
	*/
	public FLRAF getFile()
	{
		return file;
	}

 /**
	* Reads the bytes from the cache that correspond to the specified block index.
	* If the corresponding block index is not found in the cache then reads the bytes
	* from that block in the FLRAF, adds them as a cache element to the cache and returns them.
	* If the cache is full of 'modified' elements then flushes the cache and then replaces the
	* last written cache element with this new one. 
	*
	* @param	blockIndex	the index of the block in the FLRAF to be read from
	* @return				the bytes in the specified block as an array
	*/
	public byte[] read(int blockIndex) throws Exception
	{
		int targetCacheIndex = getCacheIndex(blockIndex);
		if (targetCacheIndex < 0)
		{
			byte block[] = new byte[file.getBlockSize()];
			file.read(blockIndex, block);
			writeNewElementToCache(blockIndex, block, false);
			return block;
		}
		return cache.get(targetCacheIndex).block;
	}

 /**
	* Writes the bytes to the cache at the block with the specified block index.
	* If the specified block exists in the cache then it overrides the contents of that block.
	* If the specified block does not exist in the cache then adds the block to the cache.
	* Marks each written block as 'modified.' Flushes the cache (writes each 'modified' bloc to the
	* FLRAF) when the cache is full of modified blocks. Otherwise, if the cache is full but contains 
	* unmodified blocks then overwrites an unmodified element in the cache.
	*
	* @param	blockIndex	the index of the block in the FLRAF to be written to
	* @param	block		an array of bytes to be written to the FLRAF
	*/
	public void write(int blockIndex, byte[] block) throws Exception
	{
		CacheElement element = new CacheElement(blockIndex, block, true);
		int targetCacheIndex = getCacheIndex(blockIndex);
		if (targetCacheIndex < 0)
		{
			writeNewElementToCache(element);
		}
		else
		{
			cache.set(targetCacheIndex, element);
		}
	}

	private void writeNewElementToCache(CacheElement element) throws Exception
	{
		if(cacheIsFull())
		{
			int targetIndex = getLastUnmodifiedIndex();
			//Remove the last unmodified element, push everything down the cache, and add the new element to the top
			if (targetIndex >= 0)
			{
				for (int i = targetIndex; i > 0; i--)
				{
					cache.set(i, cache.get(i - 1));	
				}
				cache.set(0, element);
			}
			//flush the cache, push everything down the cache, and add the new element to the top
			else
			{
				flush();
				cache.remove(cache.size() - 1);
				cache.add(0, element);
			}
		}
		//Add the new element to the top of the cache
		else 
		{
			cache.add(0, element);
		}
	}

	private void writeNewElementToCache(int blockIndex, byte[] block, boolean modified) throws Exception
	{
		writeNewElementToCache(new CacheElement(blockIndex, block, modified));
	}

	//returns the index where the specified block is found in the cache or -1 if it is not found
	private int getCacheIndex(int blockIndex)
	{
		for (int i = 0; i < cache.size(); i++)
		{
			if (cache.get(i) != null && cache.get(i).blockNumber == blockIndex)
			{
				return i;
			}
		}
		return -1;	
	}

	//returns the last index in the cache that is unmodified or -1 if one is not found
	private int getLastUnmodifiedIndex()
	{
		for (int i = cache.size() - 1; i >= 0 ; i--)
		{
			if (cache.get(i) != null && !cache.get(i).modified)
			{
				return i;
			}
		}
		return -1;
	}

 /**
	* Returns the next unallocated block number, either from the list of unallocated
	* block numbers, or the next block in the file.
	*
	* @return		the next unallocated block number
	*/
	public int getUnallocatedBlockNumber() throws Exception
	{	
		//Return a block from the list of deallocated blocks if one exists
		if (!unallocatedBlocks.empty())
		{
			return unallocatedBlocks.pop();
		}
		//Else returns the next largest allocated block number that has never been allocated
		else
		{
			allocatedBlockIndex++;
			return allocatedBlockIndex;
		}
	}

 /**
	* Returns the stack of unallocated block numbers
	*
	* @return		the stack of unallocated block numbers
	*/
	public Stack<Integer> getUnallocatedBlockNumbers()
	{
		return unallocatedBlocks;
	}

 /**
	* Adds the specified block number to the stack of unallocated block numbers
	*
	* @param blockNumber	the block number to be added to the stack of unallocated block numbers
	*/
	public void deallocateBlock(int blockNumber)
	{
		int index = getCacheIndex(blockNumber);
		if (index >= 0 )
		{
			cache.get(index).modified = false;
		}
		unallocatedBlocks.push(blockNumber);
	}
	
 /**
	* Flushes the cache. Writes any modified blocks to the FLRAF and marks these as 'unmodified'
	*/
	public void flush() throws Exception
	{
		for(CacheElement element : cache)
		{
			if (element.modified)
			{
				file.write(element.blockNumber, element.block);
				element.modified = false;
			}	
		}	
	}

 /**
	* Returns whether the cache is full;
	*
	* @return		whether the cache is full
	*/
	public boolean cacheIsFull()
	{
		return cache.size() == size;
	}

 /**
	* Flushes the cache and closes the FLRAF.
	*/
	public void close() throws Exception
	{
		flush();
		file.close();
	}
}
