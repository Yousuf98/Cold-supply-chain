package com.senior.OptEngineV2;


import java.io.File;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.gams.api.GAMSJob;
import com.gams.api.GAMSOptions;
import com.gams.api.GAMSWorkspace;
import com.gams.api.GAMSWorkspaceInfo;
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.EventListener;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreException;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.database.annotations.Nullable;
import com.google.firestore.v1.WriteResult;

public class Engine {
	private String date;
	public String getDate() {
		return date;
	}
	public void setDate(String date) {
		this.date = date;
	}
	private static Firestore db;
	private static InputStream serviceAccount;
	private static boolean firstRun=true;
	
	
	public Engine() { //Constructor of Engine
		
		
		//InputStream serviceAccount; //Declare inputstream for firebase-admin-json

		//db = null;
		try {
			//Initialize and connect to Firestore db
			serviceAccount = new FileInputStream("D:\\Yousuf\\Eclipse  Workspace\\OptEngine\\omarproject-284615-firebase-adminsdk-ukcvb-3b379eec7c.json");
			GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccount);
			FirebaseOptions options = new FirebaseOptions.Builder().setCredentials(credentials).build();				
			FirebaseApp.initializeApp(options);
			db = FirestoreClient.getFirestore(); 
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public synchronized static void generate(String date) {
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		System.out.println("New run: " + dtf.format(now));
		
		/*Declare and Initialize all GAMS parameters here*/

		// Total number of locations (numL)
		int numL = 11;

		// Initialize set of ALL of locations (i)
		ArrayList<Location> i = new ArrayList<Location>();
		/*
		 * i.add("DCS"); for (int j = 1; j < numL - 1; j++) { i.add("R" +
		 * Integer.toString(j)); } i.add("DCE");
		 */

		// Total number of trucks (K)
		ArrayList<String> K = new ArrayList<String>();

		// Total number of products
		ArrayList<Product> b = new ArrayList<Product>();

		// Declare distances
		ArrayList<ArrayList<Double>> S = new ArrayList<ArrayList<Double>>();
		

		// Declare Demands
		ArrayList<ArrayList<Integer>> D = new ArrayList<ArrayList<Integer>>();

		

		int N = 10; // Total locations (N)

		float W = 200; // Fixed truck cost

		int Lk = 20; // Truck capacity

		int M = 100; // Large positive number

		float fm = 77; // Fuel cost (moving)

		float fi = 26; // fuel cost (Idle)

		float v = 70; // Average speed

		float z = 20; // unloading speed

		float R = 10; // penalty for late

		double EXP = 2.71828;

		float maxTemp = 4; // Max truck temp

		float minTemp = 0; // Min truck temp

		float maxTempCost = 10; // Refrigeration Cost at Max Temp

		float costPerDegree = 1; // Refrigeration cost for lowering temp by 1 degree


		//Retrieve items from db and their respective parameters. Initialize and store them as objects in arraylist called 'b'
		
		try {

			ApiFuture<QuerySnapshot> query = db.collection("Items").get();
			QuerySnapshot querySnapshot = query.get();
			List<QueryDocumentSnapshot> itemsDocuments = querySnapshot.getDocuments();
			for (QueryDocumentSnapshot document : itemsDocuments) {
				Product p;
				String name = document.getId();
				float alpha = Float.parseFloat(document.getString("alpha"));
				float beta = Float.parseFloat(document.getString("beta"));
				float gamma = Float.parseFloat(document.getString("gamma"));
				float maxSL = Float.parseFloat(document.getString("maximumShelfLife"));
				float minSL = Float.parseFloat(document.getString("minimumShelfLife"));
				float unitPrice = Float.parseFloat(document.getString("unitPrice"));
				p = new Product(name, alpha, beta, gamma, maxSL, minSL, unitPrice);
				b.add(p);
			}

			// Retrieve locations
			query = db.collection("Locations").get();
			querySnapshot = query.get();

			ApiFuture<QuerySnapshot> queryOrders = db.collection("Orders").whereEqualTo("DeliveryDate", date).get();
			QuerySnapshot querySnapshot2 = queryOrders.get(); //Get all orders for requested date

			List<QueryDocumentSnapshot> locationsdocuments = querySnapshot.getDocuments();
			List<QueryDocumentSnapshot> ordersdocuments = querySnapshot2.getDocuments();
			numL = 0;
			Location end = null;
			
			//Check if location is warehouse. Store it at the start of arraylist 'i' if true
			for (QueryDocumentSnapshot document : locationsdocuments) {

				if (document.contains("isStarting") && document.getBoolean("isStarting")) {
					numL++;
					ArrayList<Integer> demands = new ArrayList<Integer>();
					float upper = Location.HoursToFloat(document.getString("maximumTimeWindow"));
					float lower = Location.HoursToFloat(document.getString("minimumTimeWindow"));
					for (Product x : b) {
						demands.add(0);
					}
					Location l = new Location(document.getId(), lower, upper, true, false, demands);
					// Location l = new Location("DCS", lower, upper, demands); //Needs to change!
					i.add(0, l);
					end = new Location(document.getId(), lower, upper, false, true, demands);
					;

				} 
				
				//Add every other location from locations, only if it exists as a destination within orders of requested date
				else {

					ArrayList<Integer> demands = new ArrayList<Integer>();
					float upper = Location.HoursToFloat(document.getString("maximumTimeWindow"));
					float lower = Location.HoursToFloat(document.getString("minimumTimeWindow"));
					boolean hasOrder = false;
					for (QueryDocumentSnapshot doc : ordersdocuments) {
						if (doc.getString("Destination").equalsIgnoreCase(document.getId())) {

							hasOrder = true;
							
							//If certain product from all products 'b' has zero quantity demanded, set the respective demand for that product to zero
							for (Product x : b) {
								boolean hasItem = false;
								Firestore db2 = FirestoreClient.getFirestore();
								ApiFuture<QuerySnapshot> item = db2.collection("Orders/" + doc.getId() + "/Items").get();
								QuerySnapshot snapss = item.get();
								List<QueryDocumentSnapshot> its = snapss.getDocuments();

								for (QueryDocumentSnapshot items : its) {
									if (items.getId().equalsIgnoreCase(x.getName())) {
										hasItem = true;
										Long dem = items.getLong("amount");
										demands.add(dem.intValue());
									}
								}
								if (!hasItem) {
									demands.add(0);
								}
							}
						}
					}

					if (hasOrder) {
						numL++;
						Location l = new Location(document.getId(), lower, upper, false, false, demands);
						i.add(l);

						// System.out.println("I am here 4"); //USELESS
					}
				}

			}
			i.add(end);
			numL++;
			N = (numL - 1);
			// Setup Demands in D
			for (Location l : i) {
				D.add(l.getDemand());
			}

			// Retrieve trucks
			query = db.collection("Trucks").get();
			querySnapshot = query.get();
			List<QueryDocumentSnapshot> trucksDocuments = querySnapshot.getDocuments();

			for (QueryDocumentSnapshot document : trucksDocuments) {
				K.add(document.getId());
				maxTemp = Float.parseFloat(document.getString("maxTemperature"));
				minTemp = Float.parseFloat(document.getString("minTemperature"));
				Lk = Integer.parseInt(document.getString("truckCapacity"));
			}

			// Retrieve Distances
			query = db.collection("Distances").get();
			querySnapshot = query.get();
			List<QueryDocumentSnapshot> distDocuments = querySnapshot.getDocuments();
			for (Location src : i) {
				ArrayList<Double> dist = new ArrayList<Double>();
				for (Location dest : i) {
					if (src.getName().equalsIgnoreCase(dest.getName())) {
						dist.add(0.0);
						System.out.println("Adding ETA from "+src.getName()+" to "+src.getName()+" = 0.0");
					} else {
						for (QueryDocumentSnapshot document : distDocuments) {
							if ((document.getString("source").equalsIgnoreCase(src.getName()))
									&& ((document.getString("destination").equalsIgnoreCase(dest.getName())))) {
								dist.add(document.getDouble("ETA") / 3600);
								System.out.println("Adding ETA from "+document.getString("source")+" to "+document.getString("destination")+" = "+ document.getDouble("ETA") / 3600);
							}
						}
					}
				}
				S.add(dist);
			}

			// Retrieve GAMS Parameters
			query = db.collection("GAMS").get();
			querySnapshot = query.get();
			List<QueryDocumentSnapshot> params = querySnapshot.getDocuments();
			for (QueryDocumentSnapshot document : params) {

				W = Float.parseFloat(document.getString("fixedTruckCost")); // Fixed truck cost

				M = Integer.parseInt(document.getString("largePositiveNumber")); // Large positive number

				fm = Float.parseFloat(document.getString("fuelCostMoving")); // Fuel cost (moving)

				fi = Float.parseFloat(document.getString("fuelCostIdle")); // fuel cost (Idle)

				v = Float.parseFloat(document.getString("averageSpeed")); // Average speed

				z = Float.parseFloat(document.getString("unloadingSpeed")); // unloading speed

				R = Float.parseFloat(document.getString("penaltyForLate")); // penalty for late

				EXP = Double.parseDouble(document.getString("exp"));

				maxTemp = Float.parseFloat(document.getString("maximumTruckTemperature")); // Max truck temp

				minTemp = Float.parseFloat(document.getString("minimumTruckTemperature")); // Min truck temp

				maxTempCost = Float.parseFloat(document.getString("refrigirationCost")); // Refrigeration Cost at Max
																							// Temp

				costPerDegree = Float.parseFloat(document.getString("refrigirationCost1Degree")); // Refrigeration cost
																									// for lowering temp
																									// by 1 degree

			}


			// Test printouts
			System.out.println("\nPrinting parameters retrieved from db...");
			System.out.println("\nLocations with orders for date: "+date);
			for (Location bleh : i) {
				System.out.println(bleh.getName());
				System.out.println("Lower: " + bleh.getLower());
				System.out.println("Upper: " + bleh.getUpper());
				ArrayList<Integer> dem = bleh.getDemand();
				for (int x : dem) {
					System.out.print(x + ", ");
				}
				System.out.println();
				System.out.println("Start: " + bleh.isStart());
				System.out.println("End: " + bleh.isEnd());
				System.out.println();
				System.out.println();
			}
			System.out.println();
			for (String bleh : K) {
				System.out.print(bleh + ", ");
			}
			System.out.println();
			System.out.println("min temp: " + minTemp);
			System.out.println("max temp: " + maxTemp);
			System.out.println("truck capacity: " + Lk);

			System.out.println();
			for (Product p : b) {
				System.out.println(p.getName() + " " + p.getAlpha() + " " + p.getBeta() + " " + p.getGamma() + " "
						+ p.getMaxSL() + " " + p.getMinSL() + " " + p.getUnitPrice());
			}
			System.out.println();
			System.out.println("Distances");
			for (ArrayList<Double> x : S) {
				for (Double m : x) {
					System.out.print(m + " ");
				}
				System.out.println();
			}

			System.out.println();
			System.out.println("Demands");
			for (ArrayList<Integer> x : D) {
				for (Integer m : x) {
					System.out.print(m + " ");
				}
				System.out.println();
			}
			

		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// ***Input File Formatting Module***//
		System.out.println("\nFormatting retrieved data into input excel workbook");
		
		XSSFWorkbook workbook = new XSSFWorkbook(); //Create new workbook

		// Sheet with set of all locations
		XSSFSheet sheeti = workbook.createSheet("i");
		Row irow = sheeti.createRow(0);
		Cell cell = irow.createCell(0);
		cell.setCellValue("set of all locations (i)");

		Row irow2 = sheeti.createRow(1);
		int columnCount = -1;
		for (Location l : i) {
			Cell cell2 = irow2.createCell(++columnCount);
			if (l.isStart()) {
				cell2.setCellValue("DCS");
			} else if (l.isEnd()) {
				cell2.setCellValue("DCE");
			} else {
				cell2.setCellValue(l.getName());

			}
		}

		// Sheet with set of Customer Locations
		XSSFSheet sheetsubi = workbook.createSheet("subi");
		irow = sheetsubi.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("set of customers locations (subi)");

		irow2 = sheetsubi.createRow(1);
		columnCount = -1;
		for (int j = 1; j < numL - 1; j++) {
			Cell cell2 = irow2.createCell(++columnCount);
			String str = (i.get(j)).getName();
			cell2.setCellValue(str);
		}
		
		
		// Sheet with set of Starting node + Customer Locations
		XSSFSheet sheetsub2i = workbook.createSheet("sub2i");
		irow = sheetsub2i.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("set of starting node + customers locations (sub2i)");

		irow2 = sheetsub2i.createRow(1);
		columnCount = -1;
		for (int j = 0; j < numL - 1; j++) {
			Cell cell2 = irow2.createCell(++columnCount);
			if (i.get(j).isStart()) {
				cell2.setCellValue("DCS");
			} else {
				String str = (i.get(j)).getName();
				cell2.setCellValue(str);
			}
		}

		// Sheet with set of Customer Locations subj

		XSSFSheet sheetsubj = workbook.createSheet("subj");
		irow = sheetsubj.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("set of customers locations (subj)");

		irow2 = sheetsubj.createRow(1);
		columnCount = -1;
		for (int j = 1; j < numL - 1; j++) {
			String str = (i.get(j)).getName();
			Cell cell2 = irow2.createCell(++columnCount);
			cell2.setCellValue(str);
		}

		// Sheet with set of Ending node + Customer Locations
		XSSFSheet sheetsub2j = workbook.createSheet("sub2j");
		irow = sheetsub2j.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("set of ending node + customers locations (sub2j)");

		irow2 = sheetsub2j.createRow(1);
		columnCount = -1;
		for (int j = 1; j < numL; j++) {
			Cell cell2 = irow2.createCell(++columnCount);
			if (i.get(j).isEnd()) {
				cell2.setCellValue("DCE");
			} else {
				String str = (i.get(j)).getName();
				cell2.setCellValue(str);
			}
		}

		// Sheet with Trucks (K)
		XSSFSheet sheetK = workbook.createSheet("K");
		irow = sheetK.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Trucks");

		irow2 = sheetK.createRow(1);
		columnCount = -1;
		for (int j = 0; j < K.size(); j++) {
			String str = K.get(j);
			Cell cell2 = irow2.createCell(++columnCount);
			cell2.setCellValue(str);
		}

		// Sheet with Products (b)
		XSSFSheet sheetb = workbook.createSheet("b");
		irow = sheetb.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Products");

		irow2 = sheetb.createRow(1);
		columnCount = -1;
		for (int j = 0; j < b.size(); j++) {
			String str = b.get(j).getName();
			Cell cell2 = irow2.createCell(++columnCount);
			cell2.setCellValue(str);
		}

		// Sheet with Distances(S)
		XSSFSheet sheetS = workbook.createSheet("S");
		irow = sheetS.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Distances");

		irow2 = sheetS.createRow(1);
		columnCount = 0;
		for (int j = 0; j < numL; j++) {
			cell = irow2.createCell(++columnCount);
			if (i.get(j).isStart()) {
				cell.setCellValue("DCS");
			} else if (i.get(j).isEnd()) {
				cell.setCellValue("DCE");
			} else {
				String str = i.get(j).getName();
				cell.setCellValue(str);
			}
		}
		int rowCount = 1;
		for (ArrayList<Double> l : S) {
			Row row = sheetS.createRow(++rowCount);
			columnCount = 0;
			for (Double field : l) {
				cell = row.createCell(++columnCount);
				cell.setCellValue(field);
			}
		}

		for (int j = 0; j < numL; j++) {
			Row row = sheetS.getRow(j + 2);
			cell = row.createCell(0);
			if (i.get(j).isStart()) {
				cell.setCellValue("DCS");
			} else if (i.get(j).isEnd()) {
				cell.setCellValue("DCE");
			} else {
				String str = i.get(j).getName();
				cell.setCellValue(str);
			}
		}

		// Sheet for Demands (D)
		XSSFSheet sheetD = workbook.createSheet("D");
		irow = sheetD.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Demands");

		irow2 = sheetD.createRow(1);
		columnCount = 0;
		for (int j = 0; j < b.size(); j++) {
			cell = irow2.createCell(++columnCount);
			String str = b.get(j).getName();
			cell.setCellValue(str);

		}
		rowCount = 1;
		for (ArrayList<Integer> l : D) {
			Row row = sheetD.createRow(++rowCount);
			columnCount = 0;
			for (Integer field : l) {
				cell = row.createCell(++columnCount);
				cell.setCellValue(field);
			}
		}

		for (int j = 0; j < numL; j++) {
			Row row = sheetD.getRow(j + 2);
			cell = row.createCell(0);
			if (i.get(j).isStart()) {
				cell.setCellValue("DCS");
			} else if (i.get(j).isEnd()) {
				cell.setCellValue("DCE");
			} else {
				String str = i.get(j).getName();
				cell.setCellValue(str);
			}
		}

		// Sheet for Time Windows - lower limit
		XSSFSheet sheetL = workbook.createSheet("L");
		irow = sheetL.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Time Windows - lower limit");

		irow2 = sheetL.createRow(1);
		columnCount = -1;
		for (int j = 0; j < numL; j++) {
			Cell cell2 = irow2.createCell(++columnCount);
			if (i.get(j).isStart()) {
				cell2.setCellValue("DCS");
			} else if (i.get(j).isEnd()) {
				cell2.setCellValue("DCE");
			} else {
				String str = i.get(j).getName();
				cell2.setCellValue(str);
			}
		}
		columnCount = -1;
		irow = sheetL.createRow(2);
		for (Location time : i) {
			float t = time.getLower();
			cell = irow.createCell(++columnCount);
			cell.setCellValue(t);
		}

		// Sheet for Time Windows - higher limit
		XSSFSheet sheetU = workbook.createSheet("U");
		irow = sheetU.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Time Windows - higher limit");

		irow2 = sheetU.createRow(1);
		columnCount = -1;
		for (int j = 0; j < numL; j++) {
			Cell cell2 = irow2.createCell(++columnCount);
			if (i.get(j).isStart()) {
				cell2.setCellValue("DCS");
			} else if (i.get(j).isEnd()) {
				cell2.setCellValue("DCE");
			} else {
				String str = i.get(j).getName();
				cell2.setCellValue(str);
			}
		}
		columnCount = -1;
		irow = sheetU.createRow(2);
		for (Location time : i) {
			float t = time.getUpper();
			cell = irow.createCell(++columnCount);
			cell.setCellValue(t);
		}

		// Sheet for Maximum Shelf Life of products (MSL)
		XSSFSheet sheetMSL = workbook.createSheet("MSL");
		irow = sheetMSL.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Maximum Shelf Life of products");

		irow2 = sheetMSL.createRow(1);
		columnCount = -1;
		for (int j = 0; j < b.size(); j++) {
			String str = b.get(j).getName();
			cell = irow2.createCell(++columnCount);
			cell.setCellValue(str);
		}
		columnCount = -1;
		irow = sheetMSL.createRow(2);
		for (Product m : b) {
			float msl = m.getMaxSL();
			cell = irow.createCell(++columnCount);
			cell.setCellValue(msl);
		}

		// Sheet for Minimum Shelf Life of products (minSL)
		XSSFSheet sheetminSL = workbook.createSheet("minSL");
		irow = sheetminSL.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Minimum Shelf Life");

		irow2 = sheetminSL.createRow(1);
		columnCount = -1;
		for (int j = 0; j < b.size(); j++) {
			String str = b.get(j).getName();
			cell = irow2.createCell(++columnCount);
			cell.setCellValue(str);
		}
		columnCount = -1;
		irow = sheetminSL.createRow(2);
		for (Product m : b) {
			float msl = m.getMinSL();
			cell = irow.createCell(++columnCount);
			cell.setCellValue(msl);
		}

		// Sheet for alpha of products (alpha)
		XSSFSheet sheetalpha = workbook.createSheet("alpha");
		irow = sheetalpha.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Alpha");

		irow2 = sheetalpha.createRow(1);
		columnCount = -1;
		for (int j = 0; j < b.size(); j++) {
			String str = b.get(j).getName();
			cell = irow2.createCell(++columnCount);
			cell.setCellValue(str);
		}
		columnCount = -1;
		irow = sheetalpha.createRow(2);
		for (Product m : b) {
			float alp = m.getAlpha();
			cell = irow.createCell(++columnCount);
			cell.setCellValue(alp);
		}

		// Sheet for beta of products (beta)
		XSSFSheet sheetbeta = workbook.createSheet("beta");
		irow = sheetbeta.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Beta");

		irow2 = sheetbeta.createRow(1);
		columnCount = -1;
		for (int j = 0; j < b.size(); j++) {
			String str = b.get(j).getName();
			cell = irow2.createCell(++columnCount);
			cell.setCellValue(str);
		}
		columnCount = -1;
		irow = sheetbeta.createRow(2);
		for (Product m : b) {
			float bet = m.getBeta();
			cell = irow.createCell(++columnCount);
			cell.setCellValue(bet);
		}

		// Sheet for gamma of products (gamma)
		XSSFSheet sheetgamma = workbook.createSheet("gamma");
		irow = sheetgamma.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Gamma");

		irow2 = sheetgamma.createRow(1);
		columnCount = -1;
		for (int j = 0; j < b.size(); j++) {
			String str = b.get(j).getName();
			cell = irow2.createCell(++columnCount);
			cell.setCellValue(str);
		}
		columnCount = -1;
		irow = sheetgamma.createRow(2);
		for (Product m : b) {
			float gam = m.getGamma();
			cell = irow.createCell(++columnCount);
			cell.setCellValue(gam);
		}

		// Sheet for unit price of products (P)
		XSSFSheet sheetP = workbook.createSheet("P");
		irow = sheetP.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Unit Price of product b");

		irow2 = sheetP.createRow(1);
		columnCount = -1;
		for (int j = 0; j < b.size(); j++) {
			String str = b.get(j).getName();
			cell = irow2.createCell(++columnCount);
			cell.setCellValue(str);
		}
		columnCount = -1;
		irow = sheetP.createRow(2);
		for (Product m : b) {
			float UP = m.getUnitPrice();
			cell = irow.createCell(++columnCount);
			cell.setCellValue(UP);
		}

		// Sheet for Total Locations
		XSSFSheet sheetN = workbook.createSheet("N");
		irow = sheetN.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Total Locations");

		irow = sheetN.createRow(1);
		cell = irow.createCell(0);
		cell.setCellValue(N);

		// Sheet for Fixed Truck Cost
		XSSFSheet sheetW = workbook.createSheet("W");
		irow = sheetW.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Fixed Truck Cost");

		irow = sheetW.createRow(1);
		cell = irow.createCell(0);
		cell.setCellValue(W);

		// Sheet for Truck Capacity
		XSSFSheet sheetLk = workbook.createSheet("Lk");
		irow = sheetLk.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Truck Capacity");

		irow = sheetLk.createRow(1);
		cell = irow.createCell(0);
		cell.setCellValue(Lk);

		// Sheet for Large Positive Number (M)
		XSSFSheet sheetM = workbook.createSheet("M");
		irow = sheetM.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Large Positive Number (M)");

		irow = sheetM.createRow(1);
		cell = irow.createCell(0);
		cell.setCellValue(M);

		// Sheet for Fuel Cost (Moving)
		XSSFSheet sheetfm = workbook.createSheet("fm");
		irow = sheetfm.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Fuel Cost (Moving)");

		irow = sheetfm.createRow(1);
		cell = irow.createCell(0);
		cell.setCellValue(fm);

		// Sheet for Fuel Cost (Idle)
		XSSFSheet sheetfi = workbook.createSheet("fi");
		irow = sheetfi.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Fuel Cost (Idle)");

		irow = sheetfi.createRow(1);
		cell = irow.createCell(0);
		cell.setCellValue(fi);

		// Sheet for Average Speed
		XSSFSheet sheetv = workbook.createSheet("v");
		irow = sheetv.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Average Speed");

		irow = sheetv.createRow(1);
		cell = irow.createCell(0);
		cell.setCellValue(v);

		// Sheet for Unloading Speed
		XSSFSheet sheetz = workbook.createSheet("z");
		irow = sheetz.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Unloading Speed");

		irow = sheetz.createRow(1);
		cell = irow.createCell(0);
		cell.setCellValue(z);

		// Sheet for Penalty for late
		XSSFSheet sheetR = workbook.createSheet("R");
		irow = sheetR.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Penalty for late");

		irow = sheetR.createRow(1);
		cell = irow.createCell(0);
		cell.setCellValue(R);

		// Sheet for EXP
		XSSFSheet sheetexp = workbook.createSheet("EXP");
		irow = sheetexp.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("EXP");

		irow = sheetexp.createRow(1);
		cell = irow.createCell(0);
		cell.setCellValue(EXP);

		// Sheet for Maximum Truck Temperature
		XSSFSheet sheetmaxTmp = workbook.createSheet("maxTemp");
		irow = sheetmaxTmp.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Maximum Truck Temperature");

		irow = sheetmaxTmp.createRow(1);
		cell = irow.createCell(0);
		cell.setCellValue(maxTemp);

		// Sheet for Minimum Truck Temperature
		XSSFSheet sheetminTemp = workbook.createSheet("minTemp");
		irow = sheetminTemp.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Minimum Temperature");

		irow = sheetminTemp.createRow(1);
		cell = irow.createCell(0);
		cell.setCellValue(minTemp);

		// Sheet for Refrigeration Cost at Max Temp
		XSSFSheet sheetminTempC = workbook.createSheet("maxTempCost");
		irow = sheetminTempC.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Refrigeration Cost at Max Temp");

		irow = sheetminTempC.createRow(1);
		cell = irow.createCell(0);
		cell.setCellValue(maxTempCost);

		// Sheet for Refrigeration cost for lowering temp by 1 degree
		XSSFSheet sheetminTempCC = workbook.createSheet("costPerDegree");
		irow = sheetminTempCC.createRow(0);
		cell = irow.createCell(0);
		cell.setCellValue("Refrigeration cost for lowering temp by 1 degree");

		irow = sheetminTempCC.createRow(1);
		cell = irow.createCell(0);
		cell.setCellValue(costPerDegree);

		// Write to excel file
		System.out.println("\nPRINTING TO EXCEL");
		try (FileOutputStream outputStream = new FileOutputStream(new File("GamsResources\\A-n9-k3-input-multi-vartemp.xlsx"))) {
			workbook.write(outputStream);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// GAMS setup module and Execution//
		System.out.println("\nGenerating solution using GAMS api");
		// specific workspace information is created example: C:/Desktop/Workspace
		GAMSWorkspaceInfo workspaceInfo = new GAMSWorkspaceInfo();
		workspaceInfo.setWorkingDirectory("GamsResources");
		// A new workspace is created with workspaceInfo.
		GAMSWorkspace workspace = new GAMSWorkspace(workspaceInfo);
		// workspace.setDebugLevel(DebugLevel.KEEP_FILES);
		// create GAMSOptions "opt1"
		GAMSOptions opt1 = workspace.addOptions();
		opt1.setAllModelTypes("baron");
		// run GAMSJob "t1" with GAMSOptions "opt1"
		// Creating a JOB to execute the model.
		// GAMSJob jobGams = workspace.addJobFromString(model);
		GAMSJob jobGams = workspace.addJobFromFile("A-n9-k3-multi-vartemp-model");
		// Running model
		jobGams.run(opt1);
		// System.out.println("Done");


		// **Output Reading + Formatting module**//
		System.out.println("\nReading output File produced and formatting for upload to database...");
		// Create arrays for storing routes of each truck
		ArrayList<ArrayList<String>> Routes = new ArrayList();
		for (int j = 0; j < K.size(); j++) {
			Routes.add(new ArrayList<String>());
			Routes.get(j).add(i.get(0).getName()); // Initialize each array with DCS
		}

		String excelFilePath = "GamsResources\\A-n9-k3-output-multi-vartemp.xlsx";
		Workbook wb;
		try {

			// Open excel file for reading
			FileInputStream inputStream = new FileInputStream(new File(excelFilePath));
			wb = new XSSFWorkbook(inputStream);

			org.apache.poi.ss.usermodel.Sheet firstSheet = wb.getSheetAt(0);

			// Read data and store in Routes
			int RowCounter = 14;
			int ColCounter = 2;
			Row row = firstSheet.getRow(RowCounter);
			Row rowSrc = firstSheet.getRow(RowCounter);
			String src;
			String dest = "";

			for (int j = 0; j < K.size(); j++) { // Loop for each truck

				// Obtain first retailer to visit from DCS. If none exist, this truck will not
				// be used
				double val = 0;
				RowCounter = 14;
				for (int k = 0; k < (numL); k++) {
					row = firstSheet.getRow(++RowCounter);
					val = (double) row.getCell(ColCounter).getNumericCellValue();
					if (val > 0)
						break;
				}
				if (val == 0) {
					ColCounter++;
					continue; // No routes for this truck
				}

				else {
					// Get remaining retailers to visit until back to DCE
					while (true) {
						dest = row.getCell(1).getStringCellValue();

						if (dest.equalsIgnoreCase("DCE")) {
							Routes.get(j).add(i.get(0).getName());
							break;
						} else {
							Routes.get(j).add(dest);
						}
						int index = 14;

						for (int l = 0; l < (numL * numL); l++) {
							row = firstSheet.getRow(index);
							src = row.getCell(0).getStringCellValue();
							if (src.equalsIgnoreCase(dest)) {
								break;
							}
							index++;
						}

						val = 0;
						for (int k = 0; k < (numL); k++) {

							row = firstSheet.getRow(index);
							val = (int) row.getCell(ColCounter).getNumericCellValue();
							if (val > 0)
								break;
							index++;
						}
						row = firstSheet.getRow(index);
					}
				}

				// Increment Column counter to repeat the above for the next truck
				ColCounter++;
			}

			// Close excel file
			wb.close();
			inputStream.close();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		for (ArrayList<String> r : Routes) {
			for (String t : r) {
				System.out.print(t + " - ");
			}
			System.out.println();
		}
		
		//Connecting to trips document in db
		
		
		
		ApiFuture<QuerySnapshot> docRef1 = db.collection("Trips").document(date).collection("Trucks").get();
		
		boolean isAllDeleted = false;
		List<QueryDocumentSnapshot> documents;
		try {
			documents = docRef1.get().getDocuments();
			
			ArrayList<ApiFuture> DeleteTasks = new ArrayList<ApiFuture>();
			
			
			System.out.println("\nDeleting old trips of same date\n");
			for (DocumentSnapshot document : documents) {
				System.out.println("Deleting truck "+document.getId());
				//ApiFuture Deleted = document.getReference().delete();
				//Deleted.isDone();
				DeleteTasks.add(document.getReference().delete());
			}
			
			while(!isAllDeleted) {
				int count = 0;
				for (ApiFuture items:DeleteTasks) {
					if (items.isDone()) {
						count++;
						//System.out.println("Deleted "+count+" truck(s)");
					}
				}
				if (count == DeleteTasks.size()) {
					isAllDeleted = true;
					System.out.println("All trucks deleted");
				}
			}
			
			//System.out.println("Before Sleep");
			//Thread.sleep(1500);
			//System.out.println("After Sleep");
			
			System.out.println("\nUploading generated trips to Firestore db...");
			int ind = 0;
			for (ArrayList<String> r : Routes) {

				ArrayList<String> temp = new ArrayList<String>();
				Map<String, Object> docData = new HashMap<>();
				if (r.size() > 1) {
					for (String t : r) {
						temp.add(t);
					}
					docData.put("routes", temp);
					DocumentReference docRef = db.collection("Trips").document(date);
					Map<String, Object> time = new HashMap<>();
					time.put("timeAdded", dtf.format(now));
					docRef.set(time);
					System.out.println("\nAdding truck "+K.get(ind));
					ArrayList<String> docdataaa = (ArrayList<String>) docData.get("routes");
					System.out.println("\nPrinting locations associated to truck to be uploaded to db\n");
					for (String x : docdataaa) {
						System.out.println(x+"..");
					}
					docRef.collection("Trucks").document(K.get(ind)).set(docData,SetOptions.merge());
					

				}
				ind++;
			}
		} catch (InterruptedException | ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//DocumentReference docRef = db.collection("Preplans").document("preplan");
		//docRef.update("isRunning", false); // Allow users to generate new solutions

		System.out.println("\nEnd of generation");

	}

	public static void main(String[] args) {
		/*
		try {
			serviceAccount = new FileInputStream(
					"D:\\Yousuf\\Eclipse  Workspace\\OptEngine\\omarproject-284615-firebase-adminsdk-ukcvb-3b379eec7c.json");
			GoogleCredentials credentials;
			credentials = GoogleCredentials.fromStream(serviceAccount);
			FirebaseOptions options = new FirebaseOptions.Builder().setCredentials(credentials).build();
			FirebaseApp.initializeApp(options);
		} catch (FileNotFoundException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		db = FirestoreClient.getFirestore();

		final DocumentReference docRef = db.collection("Preplans").document("preplan");
		docRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
			@Override
			public void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable FirestoreException e) {
				//System.out.println("dgfiugf");
				if (e != null) {
					System.err.println("Listen failed: " + e);
					return;
				}

				if (snapshot != null && snapshot.exists()) {
					System.out.println("Current data: " + snapshot.getData());
					String Date = snapshot.getString("deliveryDate");
					if(firstRun||Date.equalsIgnoreCase("")) {
						firstRun=false;
						}
					else {
						System.out.println("DATE: "+Date);
						docRef.update("deliveryDate", "");
						try {
							Thread.sleep(500);
						} catch (InterruptedException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
						docRef.update("isRunning", true);
						
						generate(Date);
						}
				} 
				else {
					System.out.print("Current data: null\n");
				}
			}
		});
		
		  
		while(true) {
		}
		*/
	}

}
