import java.io.*;
import java.util.*;

import Protobuf.HDFS.*;
import Utils.*;
import IDataNode.*;

import com.google.protobuf.ByteString;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;

public class Client {
	// Get NameNode from rmiregistry, not with preceding line
	private static NameNode nameNode = new NameNode();

	public static void main(String args[]) {
		/*
		if (args[0].equals("get")) {
			get_file(args[1]);
		} else if (args[0].equals("put")) {
			put_file(args[1]);
		} else if (args[0].equals("list")) {
			
		} else if (args[0].equals("debug")) {
			debug();
		}*/
		Scanner in = new Scanner(System.in);
		String line = "";
		String command = "";
		String fileName = "";
		while (true) {
			line = in.nextLine();
			try {
				String[] arguments = line.trim().split("\\s+");
				command = arguments[0];
				fileName = arguments[1];
			} catch (Exception e) {
				if (command.equals("get") || command.equals("put")) {
					System.out.println("<command> <filename>");
					continue;
				}
			}

			if (command.equals("get")) {
				get_file(fileName);
			} else if (command.equals("put")) {
				put_file(fileName);
			} else if (command.equals("list")) {

			} else if (command.equals("debug")) {
				debug();
			}
		}
	}

	private static void get_file(String fileName) {
		System.out.println(fileName);
		OpenFileRequest.Builder openFileRequest = OpenFileRequest.newBuilder();
		openFileRequest.setFileName(fileName);
		openFileRequest.setForRead(true);

		try {
			byte[] oFileRespose = nameNode.openFile(Utils.serialize(openFileRequest.build()));
			OpenFileResponse openFileResponse = (OpenFileResponse) Utils.deserialize(oFileRespose);
			List<Integer> blockList = openFileResponse.getBlockNumsList();
			BlockLocationRequest.Builder blockLocationRequest = BlockLocationRequest.newBuilder();
			for (Integer block : blockList) {
				blockLocationRequest.addBlockNums(block);
				System.out.println(block);
			}
			byte[] bLocationResponse = nameNode.getBlockLocations(Utils.serialize(blockLocationRequest.build()));
			BlockLocationResponse blockLocationResponse = (BlockLocationResponse) Utils.deserialize(bLocationResponse);
			
			int locationStatus = blockLocationResponse.getStatus();
			System.out.println("block location status: " + locationStatus);

			List<BlockLocations> blockLocations = blockLocationResponse.getBlockLocationsList(); 
			for (BlockLocations location : blockLocations) {
				DataNodeLocation dataNodeLocation = location.getLocations(0);
				String dnIP = dataNodeLocation.getIp();
				int dnPort = dataNodeLocation.getPort();
				int blockNumber = location.getBlockNumber();

				Registry registry = LocateRegistry.getRegistry();
	        	IDataNode dataNode = (IDataNode) registry.lookup("datanode");

	        	ReadBlockRequest.Builder readBlockRequest = ReadBlockRequest.newBuilder();
	        	readBlockRequest.setBlockNumber(blockNumber);

	        	byte[] rBlockResponse = dataNode.readBlock(Utils.serialize(readBlockRequest.build()));
				ReadBlockResponse readBlockResponse = (ReadBlockResponse) Utils.deserialize(rBlockResponse);
				int readBlockStatus = readBlockResponse.getStatus();
				List<ByteString> data = readBlockResponse.getDataList();
				for (ByteString str : data) {
					System.out.println(str.toStringUtf8());
				}
			}

			int status = openFileResponse.getStatus();
			int handle = openFileResponse.getHandle();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void put_file(String fileName) {
		int status;
		int fileHandle = -1;

		System.out.println("Opening file...");
		OpenFileRequest.Builder openFileRequest = OpenFileRequest.newBuilder();
		openFileRequest.setFileName(fileName);
		openFileRequest.setForRead(false);
		try {
			byte[] oFileRespose = nameNode.openFile(Utils.serialize(openFileRequest.build()));
			OpenFileResponse openFileResponse = (OpenFileResponse) Utils.deserialize(oFileRespose);
			List<Integer> blockList = openFileResponse.getBlockNumsList();
			status = openFileResponse.getStatus();
			fileHandle = openFileResponse.getHandle();

			System.out.println("file open status: " + status);
			System.out.println("file handle: " + fileHandle);
			//nameNode.test();
			for (int i=0; i<3; i++) {
				String data = "This is file content";
				AssignBlockRequest.Builder assignBlockRequest = AssignBlockRequest.newBuilder();
				assignBlockRequest.setHandle(fileHandle);

				byte[] aBlockResponse = nameNode.assignBlock(Utils.serialize(assignBlockRequest.build()));
				AssignBlockResponse assignBlockResponse = (AssignBlockResponse) Utils.deserialize(aBlockResponse);

				int assignBlockStatus = assignBlockResponse.getStatus();
				BlockLocations blockLocations = assignBlockResponse.getNewBlock();

				System.out.println("block assign status: " + assignBlockStatus);

				DataNodeLocation dataNodeLocation = blockLocations.getLocations(0);
				String dnIP = dataNodeLocation.getIp();
				int dnPort = dataNodeLocation.getPort();

				int blockNumber = blockLocations.getBlockNumber();

				System.out.println("blockNumber: " + blockNumber);

				Registry registry = LocateRegistry.getRegistry();
	        	IDataNode dataNode = (IDataNode) registry.lookup("datanode");

				//IDataNode dataNode = (IDataNode) LocateRegistry.getRegistry(dnIP, dnPort).lookup("datanode");
				
				WriteBlockRequest.Builder writeBlockRequest = WriteBlockRequest.newBuilder();
				writeBlockRequest.setBlockNumber(blockNumber);
				writeBlockRequest.addData(ByteString.copyFromUtf8(data));

				System.out.println("blockNumber: " + blockNumber);

				byte[] wBlockResponse = dataNode.writeBlock(Utils.serialize(writeBlockRequest.build()));
				WriteBlockResponse writeBlockResponse = (WriteBlockResponse) Utils.deserialize(wBlockResponse);
				int writeBlockStatus = writeBlockResponse.getStatus();

				System.out.println("block write status: " + writeBlockStatus);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		
		try {
			if (fileHandle != -1) {
				System.out.println("Closing file...");
				CloseFileRequest.Builder closeFileRequest = CloseFileRequest.newBuilder();
				closeFileRequest.setHandle(fileHandle);

				byte[] cFileRequest = nameNode.closeFile(Utils.serialize(closeFileRequest.build()));

				CloseFileResponse closeFileResponse = (CloseFileResponse) Utils.deserialize(cFileRequest);
				int closeStatus = closeFileResponse.getStatus();
				System.out.println("close file status: " + closeStatus);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		/*
		BlockLocationRequest.Builder blockLocationRequest = BlockLocationRequest.newBuilder();
		for (Integer block : blockList) {
			blockLocationRequest.addBlockNums(block);
		}

		byte[] bLocationResponse = nameNode.getBlockLocations(Utils.serialize(blockLocationRequest));
		BlockLocationResponse blockLocationResponse = (BlockLocationResponse) Utils.deserialize(bLocationResponse);

		int status = blockLocationResponse.getStatus();
		if (status == 1) {
			List<BlockLocations> blockLocationList =  blockLocationResponse.getBlockLocationsList();
			for (BlockLocations location : blockLocationList) {
				List<DataNodeLocation> dataNodeLocation = location.getLocationsList();

				DataNode dataNode;
				ArrayList<byte[]> dataBlocks = getDataBlocks(fileName);
				for (byte[] dataBlock : dataBlocks) {
					WriteBlockRequest.Builder writeBlockRequest = WriteBlockRequest.newBuilder();
					writeBlockRequest.setBlockInfo(location);
					writeBlockRequest.addData(dataBlock);

					byte[] wResponse = dataNode.writeBlock(Utils.serialize(writeBlockRequest));
					WriteBlockResponse writeBlockResponse = (WriteBlockResponse) Utils.deserialize(wResponse);
				}
			}
		} else {
			// error
		}
		*/
	}

	private static void list_files() {
		try {
			ListFilesRequest.Builder listFilesRequest = ListFilesRequest.newBuilder();
			listFilesRequest.setDirName(".");
			byte[] lFilesResponse = nameNode.list(Utils.serialize(listFilesRequest.build()));

			ListFilesResponse listFilesResponse = (ListFilesResponse) Utils.deserialize(lFilesResponse);
			/*ArrayList<String> fileList = listFilesResponse.getFileNamesList();
			for (String file : fileList) {
				System.out.println(file);
			}*/
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void debug() {
		nameNode.test();
	}

	public static ArrayList<byte[]> getDataBlocks(String fileName) {
		String content = "This is content";
		return null;
	}
}
