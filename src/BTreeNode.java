import java.util.*;

 /**
	* A B-Tree Node
	*/
public class BTreeNode<E>
{
	public int order;
	public ArrayList<E> keys;
	public ArrayList<Integer> childLinks;

 /**
	* One-arg constructor
	*
	* @param	order	the order of the B-Tree node
	*/
	public BTreeNode(int order)
	{
		this(order, new ArrayList<E>());
	} 
	
 /**
	* This constructor also takes a list of keys to be assigned to the node
	*
	* @param	order	the order of the B-Tree node
	* @param	keys	a list of keys to be assigned to the node
	*/
	public BTreeNode(int order, List<E> keys)
	{
		this.order = order;
		this.keys = new ArrayList<E>(keys);
		this.childLinks = new ArrayList<Integer>(); 
	}		
}
