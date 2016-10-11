import java.util.*;
import java.io.*;

/**
 * A Persistent B-Tree Collections class
 */
public class PersistentBTree
{
	public static final int NULL_LINK = -1;

	public CacheManager cache;
	private BTreeNodeConverter converter;
	private Comparator<String> comparator;
	private int order;
	private int minKeyCount;
	private int nodeCount;
	private int rootBlockNumber;

 /**
	* Creates a Persistent B-Tree from the properties provided. 
	* Orders the keys according to their natural ordering
	*
	* @param	order		the order of the B-Tree
	* @param	nodeSize	the size of each node in bytes
	* @param	fileName	the base filename for the flraf and header files
	* @param	cacheSize	the number of nodes the cache should be able to hold at one time
	*/
	public PersistentBTree(int order, int nodeSize, String fileName, int cacheSize) throws Exception
	{
		this(order, nodeSize, fileName, cacheSize, new NaturalOrderComparator<String>());
	}

 /**
	* Creates a Persistent B-Tree from the properties provided. 
	* Orders the keys according to the provided comparator
	*
	* @param	order		the order of the B-Tree
	* @param	nodeSize	the size of each node in bytes
	* @param	fileName	the base filename for the flraf and header files
	* @param	cacheSize	the number of nodes the cache should be able to hold at one time
	* @param 	comparator	a comparator to be used to order the keys in the tree
	*/
	public PersistentBTree(int order, int nodeSize, String fileName, int cacheSize, Comparator<String> comparator) throws Exception
	{
		this.comparator = comparator;
		this.order = order;
		FLRAF file = new FLRAF(fileName, "rw", nodeSize);
		this.cache = new CacheManager(cacheSize, file);
		this.converter = new BTreeNodeConverter(order, nodeSize);
		this.rootBlockNumber = NULL_LINK;
		this.minKeyCount = (int)Math.ceil(order / 2.0) - 1;
		this.nodeCount = 0;
	}

 /**
	* Creates a Persistent B-Tree using the properties provided in the specified header file.
	* Orders the keys based on their natural ordering
	*
	* @param	headerFile	the name of the header file
	* @param	cacheSize	the number of nodes the cache should be able to hold at one time 
	*/
	public PersistentBTree(String headerFile, int cacheSize) throws Exception
	{
		this(headerFile, cacheSize, new NaturalOrderComparator<String>());
	}

 /**
	* Creates a Persistent B-Tree using the properties provided in the specified header file.
	* Orders the keys based on the specified comparator
	*
	* @param	headerFile	the name of the header file
	* @param	cacheSize	the number of nodes the cache should be able to hold at one time 
	* @param	comparator	a comparator to be used to order the keys
	*/
	public PersistentBTree(String headerFile, int cacheSize, Comparator<String> comparator) throws Exception
	{
 		ObjectInputStream ois = null;
        ois = new ObjectInputStream(new FileInputStream(headerFile));
		HashMap<String, Object> headerData = (HashMap<String, Object>)ois.readObject();
        ois.close();

		this.comparator = comparator;
		this.order = (int)headerData.get("order");
		FLRAF file = new FLRAF((String)headerData.get("fileName"), "rw", (int)headerData.get("nodeSize"));
		this.cache = new CacheManager(cacheSize, file);
		this.converter = new BTreeNodeConverter(order, (int)headerData.get("nodeSize"));
		this.rootBlockNumber = (int)headerData.get("rootBlockNumber");
		this.minKeyCount = (int)Math.ceil(order / 2.0) - 1;
		this.nodeCount = (int)headerData.get("nodeCount");
	}
	
   /**
	* Attempts to add a value to the B-Tree. Does not add duplicates.
	*
	* @param	value	the value to be added to the BST
	* @return			the result of the add (whether it was successful or not)
	*/
	public boolean add(String value) throws Exception
	{
		if (isEmpty())
		{
			BTreeNode<String> node = new BTreeNode<String>(order);
			node.keys.add(value);
			
			this.rootBlockNumber = cache.getUnallocatedBlockNumber();
			cache.write(rootBlockNumber, converter.writeToBytes(node));
			nodeCount++;
			
			updateHeaderFile();
			return true;
		}
		if (topDownInsert(rootBlockNumber, NULL_LINK, null, value))
		{
			updateHeaderFile();
			return true;
		}
		
		return false;
	}

	private boolean topDownInsert(int localRootBlockNumber, int parentBlockNumber, BTreeNode<String> parent, String value) throws Exception
	{
		boolean wasAdded = true;
		BTreeNode<String> localRoot = getNode(localRootBlockNumber);

		//if localRoot is full then split
		if (localRoot.keys.size() == order - 1)
		{
			localRoot = splitNode(localRootBlockNumber, parentBlockNumber, parent);
		}	
			
		int targetKeyIndex = getKeyIndex(localRoot, value);	
		
		//checks if value is a duplicate
		if (localRoot.keys.size() > targetKeyIndex && comparator.compare(localRoot.keys.get(targetKeyIndex), value) == 0) return false;

		//if local root is a leaf add to local root
		if (localRoot.childLinks.size() == 0)
		{
			localRoot.keys.add(targetKeyIndex, value);		
			cache.write(localRootBlockNumber, converter.writeToBytes(localRoot));
		}
		
		//if local root is not a leaf then move to child
		else
		{
			wasAdded = topDownInsert(localRoot.childLinks.get(targetKeyIndex), localRootBlockNumber, localRoot, value);
		}
		return wasAdded;
	}

	//splits the node into two and promotes the middle value to the parent node
	private BTreeNode<String> splitNode(int blockNumber, int parentBlockNumber, BTreeNode<String> parent) throws Exception
	{	
		BTreeNode<String> node = getNode(blockNumber);

		//if node is root, create a new node to be the node's parent
		//this parent will replace the node as root
		if (parent == null)
		{
			parent = new BTreeNode<String>(order);
			parent.childLinks.add(blockNumber);
		}

		//find middle value
		int midIndex = (int)Math.round(node.keys.size() / 2.0) - 1;
		String keyToPromote = node.keys.get(midIndex);

		//Add key to promote to parent node
		int targetKeyIndex = getKeyIndex(parent, keyToPromote);
		parent.keys.add(targetKeyIndex, keyToPromote);
		
		//Split node in two around the key that was promoted
		BTreeNode<String> leftNode = new BTreeNode<String>(order);
		BTreeNode<String> rightNode = new BTreeNode<String>(order);
		leftNode.keys = new ArrayList<String>(node.keys.subList(0, midIndex));
		rightNode.keys = new ArrayList<String>(node.keys.subList(midIndex + 1, node.keys.size()));

		if (node.childLinks.size() > 0)
		{
			leftNode.childLinks = new ArrayList<Integer>(node.childLinks.subList(0, midIndex + 1));
			rightNode.childLinks = new ArrayList<Integer>(node.childLinks.subList(midIndex + 1, node.childLinks.size()));
		}
		
		//Write split nodes to the cache
		int leftBlockNumber = blockNumber;
		int rightBlockNumber = cache.getUnallocatedBlockNumber();
		cache.write(leftBlockNumber, converter.writeToBytes(leftNode));
		cache.write(rightBlockNumber, converter.writeToBytes(rightNode));

		//Connect parent to the two split nodes
		parent.childLinks.set(targetKeyIndex, leftBlockNumber);
		parent.childLinks.add(targetKeyIndex + 1, rightBlockNumber);

		if (parentBlockNumber == NULL_LINK)
		{
			//Set parent as new root 
			parentBlockNumber = cache.getUnallocatedBlockNumber();
			rootBlockNumber = parentBlockNumber;
		}
		cache.write(parentBlockNumber, converter.writeToBytes(parent));

		nodeCount++;
		return parent;
	}

	//Returns an integer that corresponds the key index of the value if it were added to the specified node as a key
	//If the value would be added to the end of the node's list of keys the index this method returns will be equal size of that list
	private int getKeyIndex(BTreeNode<String> node, String value)
	{
		Iterator<String> keyIterator = node.keys.iterator();
		int index = 0;

		while (keyIterator.hasNext() && comparator.compare(value, keyIterator.next()) > 0)
		{
			index++;
		}

		return index;
	}

	private BTreeNode<String> getNode(int blockNumber) throws Exception
	{
		if (blockNumber == NULL_LINK) return null;
		byte[] nodeAsBytes = cache.read(blockNumber);
		return converter.readFromBytes(nodeAsBytes);
	}

   /**
	* Attempts to remove a value from the B-Tree.
	* 
	* @param	value	the value to be removed
	* @return			the result of the removal
	*/
	public boolean remove(String value) throws Exception
	{
		if (delete(rootBlockNumber, null, value))
		{
			updateHeaderFile();
			return true;
		}
		return false;
	}

	private boolean delete(int localRootBlockNumber, BTreeNode<String> parent, String value) throws Exception
	{
		BTreeNode<String> localRoot = getNode(localRootBlockNumber);
		if (localRoot == null) return false;

		int targetKeyIndex = getKeyIndex(localRoot, value);

		//localRoot does not contain key
		if (targetKeyIndex == localRoot.keys.size() || comparator.compare(localRoot.keys.get(targetKeyIndex), value) != 0)
		{
			if (localRoot.childLinks.size() == 0) return false;
			if (!delete(localRoot.childLinks.get(targetKeyIndex), localRoot, value)) return false;
		}
		//localRoot contains key, but is an internal node
		else if (localRoot.childLinks.size() > 0)
		{
			//Replace target key with predecessor and recursively remove predecessor from its original position
			String predecessorKey = getPredecessorKey(localRoot, targetKeyIndex);
			localRoot.keys.set(targetKeyIndex, predecessorKey);
			delete(localRoot.childLinks.get(targetKeyIndex), localRoot, predecessorKey);
		}
		//localRoot contains key, and is a leaf
		else
		{
			localRoot.keys.remove(targetKeyIndex);
		}		
		
		//If localRoot has fewer than the minimum key count then redistribute keys
		if (localRoot.keys.size() < minKeyCount)
		{
			redistributeKeys(localRoot, parent, value);
		}

		//local root is root and is empty; remove local root
		if (parent == null && localRoot.keys.size() == 0)
		{
			if (localRoot.childLinks.size() == 0)
			{
				//tree is empty; remove root node
				rootBlockNumber = NULL_LINK;
			}
			else
			{
				//set node's only child to be the new root
				rootBlockNumber = localRoot.childLinks.get(0);
			}
			cache.deallocateBlock(localRootBlockNumber);
			nodeCount--;
		}
		else
		{
			cache.write(localRootBlockNumber, converter.writeToBytes(localRoot));
		}
		return true;
	}

	private String getPredecessorKey(BTreeNode<String> node, int targetKeyIndex) throws Exception
	{
		if (node.childLinks.size() == 0) return null;
		BTreeNode<String> currentNode = getNode(node.childLinks.get(targetKeyIndex));
		while (currentNode.childLinks.size() > 0)
		{
			int childBlockNumber = currentNode.childLinks.get(currentNode.childLinks.size() - 1);
			currentNode = getNode(childBlockNumber);
		}
		return currentNode.keys.get(currentNode.keys.size() - 1 );
	}

	private void redistributeKeys(BTreeNode<String> localRoot, BTreeNode<String> parent, String key) throws Exception
	{
		if (parent == null) return;

		int childIndex = getKeyIndex(parent, key);
		int localRootBlockNumber = parent.childLinks.get(childIndex);

		int leftSiblingBlockNumber = childIndex > 0 ? parent.childLinks.get(childIndex - 1) : -1;
		int rightSiblingBlockNumber = childIndex < parent.keys.size() ? parent.childLinks.get(childIndex + 1) : -1;
		BTreeNode<String> leftSibling = childIndex > 0 ? getNode(leftSiblingBlockNumber) : null;
		BTreeNode<String> rightSibling = childIndex < parent.keys.size() ? getNode(rightSiblingBlockNumber) : null;
		
		//Steal from left
		if (leftSibling != null && leftSibling.keys.size() > minKeyCount)
		{
			//Pull down key from parent to localRoot
			localRoot.keys.add(0, parent.keys.get(childIndex - 1));

			//Pull up key from left sibling to parent
			String keyToPromote = leftSibling.keys.get(leftSibling.keys.size() - 1);
			parent.keys.set(childIndex - 1, keyToPromote);
			leftSibling.keys.remove(leftSibling.keys.size() - 1);

			//Add subtree of stolen key to original node
			if (localRoot.childLinks.size() > 0)
			{
				localRoot.childLinks.add(0, leftSibling.childLinks.get(leftSibling.childLinks.size() - 1));
				leftSibling.childLinks.remove(leftSibling.childLinks.size() - 1);
			}

			cache.write(leftSiblingBlockNumber, converter.writeToBytes(leftSibling));
		}
		//Steal from right
		else if (rightSibling != null && rightSibling.keys.size() > minKeyCount)
		{
			//Pull down key from parent to localRoot
			localRoot.keys.add(parent.keys.get(childIndex));

			//Pull up key from right sibling to parent
			String keyToPromote = rightSibling.keys.get(0);
			parent.keys.set(childIndex, keyToPromote);
			rightSibling.keys.remove(0);

			//Add subtree of stolen key to original node
			if (localRoot.childLinks.size() > 0)
			{
				localRoot.childLinks.add(rightSibling.childLinks.get(0));
				rightSibling.childLinks.remove(0);
			}
			cache.write(rightSiblingBlockNumber, converter.writeToBytes(rightSibling));
		}
		//Merge nodes
		else
		{
			// merge with left sibling
			if (leftSibling != null)
			{
				//Copy keyToDemote to localRoot
				int indexOfKeyToDemote = childIndex - 1;
				localRoot.keys.add(0, parent.keys.get(indexOfKeyToDemote));
				parent.keys.remove(indexOfKeyToDemote);

				//Combine leftSibling into localRoot
				localRoot.keys.addAll(0, leftSibling.keys);
				localRoot.childLinks.addAll(0, leftSibling.childLinks);
				parent.childLinks.remove(childIndex - 1);

				cache.deallocateBlock(leftSiblingBlockNumber);
			}
			//merge with right sibling
			else
			{
				//Copy keyToDemote to localRoot
				int indexOfKeyToDemote = childIndex;
				localRoot.keys.add(parent.keys.get(indexOfKeyToDemote));
				parent.keys.remove(indexOfKeyToDemote);

				//Combine rightSibling into localRoot
				localRoot.keys.addAll(rightSibling.keys);
				localRoot.childLinks.addAll(rightSibling.childLinks);
				parent.childLinks.remove(childIndex + 1);

				cache.deallocateBlock(rightSiblingBlockNumber);
			}	

			nodeCount--;
		}
	}

	private int getLastKeyIndex(BTreeNode<String> node)
	{
		return node.keys.size() > 0 ? node.keys.size() - 1 : 0;
	}

   /**
	* Tests whether the a value is contained in the B-Tree
	* 
	* @param	value	the value to be tested
	* @return			the result of the test (whether or not it was found in the tree)
	*/
	public boolean contains(String value) throws Exception
	{
		BTreeNode<String> localRoot = getNode(rootBlockNumber);
		if (localRoot == null) return false;
		int targetKeyIndex = getKeyIndex(localRoot, value);
		while (targetKeyIndex == localRoot.keys.size() || comparator.compare(localRoot.keys.get(targetKeyIndex), value) != 0)
		{
			if (localRoot.childLinks.size() == 0) return false;
			localRoot = getNode(localRoot.childLinks.get(targetKeyIndex));
			targetKeyIndex = getKeyIndex(localRoot, value);
		}
		return true;
	}

   /**
	* Tests whether the B-Tree is empty.
	*
	* @return	whether the B-Tree is empty
	*/
	public boolean isEmpty() throws Exception
	{
		return rootBlockNumber == NULL_LINK;
	}

   /**
	* Returns the size of the tree in bytes
	*
	* @return	the size of the tree in bytes
	*/
	public int getSize()
	{
		return nodeCount * converter.nodeSize;
	}

   /**
	* Closes the flraf associated with the Persistent B-Tree and updates the header file
	*
	*/
	public void close() throws Exception
	{
		cache.close();
		updateHeaderFile();
	}

	private void updateHeaderFile() throws Exception
	{
		HashMap<String, Object> headerFileData = new HashMap<String, Object>();
		headerFileData.put("fileName", cache.getFile().getFileName());
		headerFileData.put("order", order);
		headerFileData.put("nodeSize", converter.nodeSize);
		headerFileData.put("nodeCount", nodeCount);
		headerFileData.put("treeSize", getSize());
		headerFileData.put("rootBlockNumber", rootBlockNumber);
		headerFileData.put("unallocatedBlocks", cache.getUnallocatedBlockNumbers());
  		FileOutputStream fos = null;
	    ObjectOutputStream oos = null;

        try 
        {
			String flrafName = cache.getFile().getFileName();
			String headerFileName = flrafName.substring(0, flrafName.length() - 5) + "hdr";
            fos = new FileOutputStream(headerFileName);
            oos = new ObjectOutputStream(fos);

            oos.writeObject(headerFileData);
			oos.close();
        }
        catch (IOException ex) 
        {
            System.err.println("Error writing objects: "+ex.getMessage());
        }
	}
}
