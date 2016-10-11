import java.net.*;
import java.io.*;

public class BTreeService {
	static final int PORT = 2333;
	static final int ORDER = 8;
	static final int CACHE_SIZE = 4;
	static final int NODE_SIZE = 256;
	static final String HEADER_FILE_NAME = "words.hdr"; 
	static final String FLRAF_FILE_NAME = "words.flraf";
	static final String TEXT_FILE_NAME = "words.txt";

  	public static void main(String[] args) throws Exception {
    	try {
			ServerSocket serverSocket = new ServerSocket(PORT);
	  		System.out.println("Server running on port " + PORT);			

			System.out.println("Service is ready");

			PersistentBTree tree = loadTree();
			boolean treeIsOpen = true;
 
			while (true) {
				Socket client = serverSocket.accept();

				PrintWriter out = new PrintWriter(client.getOutputStream(), true);
				BufferedReader in =  
				  new BufferedReader(new InputStreamReader(client.getInputStream()));

				String cmd = in.readLine();
				String resultMsg = "";

				if (cmd != null)
				{
					String request = cmd.substring(5, cmd.length() - 9);

					if(!treeIsOpen)
					{
						tree = loadTree();
						treeIsOpen = true;
					}
					
					if (request.startsWith("-"))
					{
						request = request.substring(1);
						if (tree.remove(request))
						{
							resultMsg = "'" + request + "' was REMOVED from the dictionary";
						}
						else
						{
							resultMsg = "'" + request + "' was NOT FOUND in the dictionary and could not be removed";
						}
					}
					else if (request.startsWith("?"))
					{
						tree.close();
						treeIsOpen = false;
						resultMsg = "the B-tree was closed";
					}
					else if(request.length() > 0)
					{
						if (tree.add(request))
						{
							resultMsg = "'" + request + "' was ADDED to the dictionary";
						}
						else
						{
							resultMsg = "'" + request + "' was FOUND in the dictionary";
						}
					}
					else
					{
						resultMsg = "Please Enter a Command";
					}

					String reply = "<html>\n" +
						"<head><title>Persistent B-Tree</title></head>\n" + 
						"Got request:" + cmd + "<br><br>\n " +
						resultMsg +
						"\n</html>\n";

					int len = reply.length();

					out.println("HTTP/1.0 200 OK");
					out.println("Content-Length: " + len);
					out.println("Content-Type: text/html\n");
					out.println(reply);
				}
				out.close();
				in.close();
				client.close();	
			}	
		}
		catch (IOException ex) {
			ex.printStackTrace();
		  	System.exit(-1);
    	}
  	}

	private static PersistentBTree loadTree() throws Exception
	{
 		File headerFile = new File(HEADER_FILE_NAME);

		if (headerFile.isFile())
		{
			//open the existing B-tree
			System.out.println("Opening B-Tree");
			return new PersistentBTree(HEADER_FILE_NAME, CACHE_SIZE);
		}
		else
		{
			//create a new B-tree and add all words from words.txt into it
			System.out.println("Creating new B-Tree");
			PersistentBTree tree = new PersistentBTree(ORDER, NODE_SIZE, FLRAF_FILE_NAME, CACHE_SIZE);
			
			System.out.println("Adding words");
			BufferedReader br = new BufferedReader(new FileReader(TEXT_FILE_NAME));
			String line = br.readLine();

			int wordCount = 0;
			while (line != null) 
			{
				tree.add(line);
				
				if (wordCount % 1000 == 0) 
				{
					System.out.println("Added " + wordCount + " words");
				}
				
				wordCount++;
				line = br.readLine();
			}
			System.out.println("Successfully added " + wordCount + " words");
			return tree;
		}
	}
}
