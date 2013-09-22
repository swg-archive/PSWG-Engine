package engine.clientdata.visitors;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;

import org.apache.mina.core.buffer.IoBuffer;

import engine.clientdata.VisitorInterface;

public class CrcStringTableVisitor implements VisitorInterface {

	int count;
	ArrayList<Integer> crcList;
	ArrayList<Integer> startList;
	ArrayList<String> stringList;
	
	public CrcStringTableVisitor() {
		crcList = new ArrayList<Integer>();
		startList = new ArrayList<Integer>();
		stringList = new ArrayList<String>();
	}
	
	@Override
	public void parseData(String nodename, IoBuffer data, int depth, int size) throws Exception {
		
		if(nodename.equals("0000DATA")) {
			
			count = data.getInt();
			
		} else if(nodename.equals("CRCT")) {
			
			for(int i=0; i < count; ++i) {
				
				crcList.add(data.getInt());

			}
			
		} else if(nodename.equals("STRT")) {
			
			for(int i=0; i < count; ++i) {
				
				startList.add(data.getInt());
				
			}
			
		} else if(nodename.equals("STNG")) {
			
			CharsetDecoder cd = Charset.forName("US-ASCII").newDecoder();
			
			for(int i=0; i < count; ++i) {
				
				data.position(startList.get(i));
				stringList.add(data.getString(cd));
				cd.reset();
				
			}
		}
	}

	public boolean isValidCRC(int crc) {
		
		if(!crcList.contains(crc))
			return false;
		return true;
		
	}
	
	public void dispose() {
		crcList.clear();
		startList.clear();
		stringList.clear();
	}
	
	@Override
	public void notifyFolder(String nodeName, int depth) throws Exception {}

}
