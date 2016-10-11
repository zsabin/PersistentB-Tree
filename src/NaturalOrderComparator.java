import java.util.Comparator;
import java.lang.Comparable;
import java.io.Serializable;

/**
 * NaturalComparator is a wrapper class for T's compareTo --
 * @author EWenderholm
 */
public class NaturalOrderComparator<T extends Comparable<T>> implements Comparator<T>, Serializable
{
    /**
     * uses T's compareTo method to determine the ordering
     */
	public int compare(T o1, T o2)
	{
		return o1.compareTo(o2);
	}

    public boolean equals(Object obj) 
	{
        return (obj!=null) && (obj instanceof NaturalOrderComparator);
    }
}
