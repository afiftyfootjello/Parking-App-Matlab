package ui;

import java.io.FileNotFoundException;
import java.util.Calendar;
import java.util.GregorianCalendar;

import javafx.application.Platform;

/**
 * Class responsible for processing and update scheduling
 * 
 * @author Kyle Cochran
 * @version 1.0
 * @created 18-Feb-2016 11:36:22 AM
 */
@SuppressWarnings("deprecation")
public class ProcessingManager implements Runnable {

	private volatile DisplayUI ui;
	public volatile double bkgRefreshFreq;
	public volatile double paintRefreshFreq;
	public volatile double infoRefreshFreq;
	public HistoryHandler hH;
	public volatile boolean procOn;
	private volatile boolean timeToUpdate;
	private volatile boolean okayToUpdate = true;
	private Thread t;
	HistoryHandler history;
	private boolean[] sharedData;
	
	CameraDriver cd = new CameraDriver();
	int[][] lines = getSpotMatrix();
	Calendar cal = Calendar.getInstance();
	boolean standalone;

	//Runnable objects allow scheduling tasks to the UI to prevent thread errors
	//They are method calls run by using: Platform.runLater(RunnableObject)
	Runnable scheduledBkgUpdate = new Runnable() {
		@Override
		public void run() {updateUIBkg();}
	};
	Runnable scheduledSpotDrawing = new Runnable() {
		@Override
		public void run() {updateUISpots();}
	};
	Runnable scheduledInfoChange = new Runnable() {
		@Override
		public void run() {updateUIInfo();}
	};
	Runnable scheduledAddGraphs = new Runnable() {
		@Override
		public void run() {addGraphs();}
	};


	/**
	 * Default constructor. Auto-sets refresh frequency to 1 per second.
	 */
	public ProcessingManager(boolean[] sharedData) {
		bkgRefreshFreq = 1; // indicates that analysis should refresh once per
		// second
		bkgRefreshFreq = 20.0000;
		paintRefreshFreq = 0.1;
		infoRefreshFreq = 1.0;
		procOn = false;
		hH = new HistoryHandler();
		standalone = false;
		this.sharedData=sharedData;
	}

	/**
	 * Constructs with a custom refresh frequency.
	 * 
	 * @param rf
	 *            an integer. (refreshes per second)
	 */
	public ProcessingManager(double rf, boolean standalone, boolean[] sharedData) {
		bkgRefreshFreq = rf;
		paintRefreshFreq = 0.2;
		infoRefreshFreq = 1.0;
		procOn = false;
		hH = new HistoryHandler();
		this.standalone = standalone;
		this.sharedData=sharedData;
	}

	/**
	 * Method used to begin thread. Checks for pre-existing threads, then
	 * initializes and begins.
	 */
	public void beginProcThread() {

		if (t == null) {
			t = new Thread(this, "proc-thread");
			t.start();
		} else {
			System.out.println(
					"Error: The processing thread failed to initialize. This was likely caused by the presence of a pre-existing processing thread");
		}
	}

	/**
	 * Method to terminate thread. Changes boolean to end processing loop, waits
	 * to let the thread finish current loop, then attempts to erase the thread
	 * from memory.
	 */
	public void endProcThread() {

		procOn = false; // make the processing loop able to end

		try {
			t.join(); // waits for the thread to die naturally
		} catch (InterruptedException e) {
			System.out.println("Error: The thread was interrupted when trying to finish execution. How rude.");
			e.printStackTrace();
		}

		if (!t.isAlive()) {
			t = null; // if the thread died successfully, clear the variable
		} else {
			System.out.println("Error: killing the thread failed. Try harder next time.");
		}
	}

	/**
	 * The entry point for the thread. Must be implemented when class implements
	 * "Runnable". Updates "currentSpots" in a timed loop.
	 */
	public void run() {
		int procCount = 0;
		int minutes;
		procOn = true;
		boolean waiting = true;

		//Waiting for the UI to boot up so that we can reference and update UI objects
		if(!standalone){
			while (waiting){
				try{
					if(!RiddleRunAroundParking.ui.equals(null)){
						waiting = false;
					}
				}catch(NullPointerException e){
					System.out.println("Waiting for UI to fully initialize.....");
				}
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					System.out.println("Yo dude, the thread got interupted");
				}
			}

			updateUIBkg();
			//after we're sure that the UI is loaded, we'll replace the dummy graphs with real ones
			Platform.runLater(scheduledAddGraphs);
			//We will also paint the spots and update the percent
			Platform.runLater(scheduledSpotDrawing);
			Platform.runLater(scheduledInfoChange);
		}

		//Enter the continuous processing loop
		while (procOn) {
			try {
				//pause between loops
				Thread.sleep((long) (1000 / bkgRefreshFreq));
			} catch (InterruptedException e) {
				System.out.println("Yo dude, the thread got interupted");
				e.printStackTrace();
			}

			//within the loop, if the timing also coincides with the timing for repainting spots or info on UI, then run those operations
			if(paintRefreshFreq*procCount/bkgRefreshFreq>=1){
				if(!standalone){Platform.runLater(scheduledSpotDrawing);}//using new data, repaint spots
			}else if(infoRefreshFreq*procCount/bkgRefreshFreq>=1){
				if(!standalone){Platform.runLater(scheduledInfoChange);}//refresh clock and percent full
			}else if(procCount>100){
				procCount=0;//can't let the counter get too high
			}


			//The background image will update every loop (loop timing defined by: bkgRefreshFreq)
			if(!standalone){updateUIBkg();}

			/*/ logic to update history at certain times of day-------------------
			minutes = GregorianCalendar.getInstance().getTime().getMinutes();

			// checks whether it's 00 or 30 minutes into the hour
			timeToUpdate = (minutes == 0 || minutes == 30);

			// if it's 00 or 30 minutes in an hour, and we haven't updated yet,
			// add the spots to history, deactivate update switch
			if (timeToUpdate && okayToUpdate) {
				hH.appendCurrentTime(currentSpots);
				try{
					hH.saveAsPlainText();
				}catch(FileNotFoundException e){
					System.err.println("Error in history writer. Unable to save recent as plain text");
				}
				okayToUpdate = false;
			}

			// if we're past update time, turn the switch back on.
			if (minutes == 1 || minutes == 31) {
				okayToUpdate = true;
			}
			*/// ------------------------------------------------------------------

			procCount++;
		}

	}

	/**
	 * Wrapper method to return currentSpots variable.
	 * 
	 * @return currentSpots an array of integers that represents the current
	 *         state of the lot.
	 */
	public boolean[] getCurrentSpots() {
		return sharedData;
	}

	/**
	 * Calculates the current percent full of the lot
	 * 
	 * @return an int that represents the current percent full of the lot
	 */
	public int getCurrentPercent() {
		int total = 0;
		for (int i = 0; i < sharedData.length; i++) {
			total += sharedData[i] ? 1:0; //returns 1 if true, 0 if false
		}
		return 100 * total / sharedData.length;
	}

	/**
	 * An access method that allows a UI reference to be set. This gives the ProcessingManager
	 * accesss to UI elements.
	 * 
	 * @param ui a DisplayUI object that runs in tandem with the processing loop
	 */
	public void setUIRef(DisplayUI ui){
		this.ui = ui;
	}


	/**
	 * An error catching wrapper method that updates the lot background image
	 */
	public synchronized void updateUIBkg(){
		try{
			cd.updateUILiveFeed();
		}catch(NullPointerException e){
			System.out.println("there was a null pointer when updating UI background from PM"); 

		}
	}

	/**
	 * An error catching wrapper method that updates the UI info panel
	 */
	public synchronized void updateUIInfo(){
		try{
			ui.updateUIPercent(getCurrentPercent());
		}catch(NullPointerException e){
			System.out.println("there was a null pointer when updating UI info panel from PM");
		}
	}

	/**
	 * An error catching wrapper method that repaints spots on the UI
	 */
	public synchronized void updateUISpots(){
		try{			ui.lineColor();
		}catch(NullPointerException e){
			System.out.println("there was a null pointer when painting new UI spots from PM"); 

		}
	}

	/**
	 * An error catching wrapper method that
	 */
	public synchronized void addGraphs(){
		ui.addGraphs();
	}
	

	/**
	 * Wrapper method to define the lines variable to our specific case
	 */
	private void generateSpotMatrix() {
		lines = new int[32][4];

		int offset = 0;

		lines[0][0] = 200;
		lines[0][1] = 224 + offset;
		lines[0][2] = 190;
		lines[0][3] = 255 + offset;

		lines[1][0] = 227;
		lines[1][1] = 225 + offset;
		lines[1][2] = 219;
		lines[1][3] = 258 + offset;

		lines[2][0] = 262;
		lines[2][1] = 228 + offset;
		lines[2][2] = 260;
		lines[2][3] = 260 + offset;

		lines[3][0] = 300;
		lines[3][1] = 231 + offset;
		lines[3][2] = 303;
		lines[3][3] = 261 + offset;

		lines[4][0] = 334;
		lines[4][1] = 231 + offset;
		lines[4][2] = 343;
		lines[4][3] = 265 + offset;

		// Grass area between these lines

		lines[5][0] = 374;
		lines[5][1] = 234 + offset;
		lines[5][2] = 386;
		lines[5][3] = 265 + offset;

		lines[6][0] = 408;
		lines[6][1] = 234 + offset;
		lines[6][2] = 424;
		lines[6][3] = 266 + offset;

		lines[7][0] = 445;
		lines[7][1] = 240 + offset;
		lines[7][2] = 460;
		lines[7][3] = 268 + offset;

		lines[8][0] = 478;
		lines[8][1] = 242 + offset;
		lines[8][2] = 495;
		lines[8][3] = 270 + offset;

		lines[9][0] = 504;
		lines[9][1] = 242 + offset;
		lines[9][2] = 525;
		lines[9][3] = 271 + offset;

		lines[10][0] = 535;
		lines[10][1] = 245 + offset;
		lines[10][2] = 560;
		lines[10][3] = 273 + offset;

		lines[11][0] = 561;
		lines[11][1] = 245 + offset;
		lines[11][2] = 591;
		lines[11][3] = 272 + offset;

		// New row

		lines[12][0] = 200;
		lines[12][1] = 275 + offset;
		lines[12][2] = 189;
		lines[12][3] = 322 + offset;

		lines[13][0] = 240;
		lines[13][1] = 278 + offset;
		lines[13][2] = 235;
		lines[13][3] = 326 + offset;

		lines[14][0] = 282;
		lines[14][1] = 279 + offset;
		lines[14][2] = 280;
		lines[14][3] = 329 + offset;

		lines[15][0] = 321;
		lines[15][1] = 282 + offset;
		lines[15][2] = 327;
		lines[15][3] = 331 + offset;

		lines[16][0] = 360;
		lines[16][1] = 283 + offset;
		lines[16][2] = 374;
		lines[16][3] = 332 + offset;

		lines[17][0] = 402;
		lines[17][1] = 285 + offset;
		lines[17][2] = 418;
		lines[17][3] = 333 + offset;

		lines[18][0] = 440;
		lines[18][1] = 286 + offset;
		lines[18][2] = 459;
		lines[18][3] = 333 + offset;

		lines[19][0] = 474;
		lines[19][1] = 286 + offset;
		lines[19][2] = 500;
		lines[19][3] = 334 + offset;

		lines[20][0] = 509;
		lines[20][1] = 289 + offset;
		lines[20][2] = 536;
		lines[20][3] = 332 + offset;

		lines[21][0] = 543;
		lines[21][1] = 290 + offset;
		lines[21][2] = 570;
		lines[21][3] = 330 + offset;

		lines[22][0] = 571;
		lines[22][1] = 292 + offset;
		lines[22][2] = 600;
		lines[22][3] = 331 + offset;

		lines[23][0] = 606;
		lines[23][1] = 290 + offset;
		lines[23][2] = 632;
		lines[23][3] = 329 + offset;

		lines[24][0] = 632;
		lines[24][1] = 294 + offset;
		lines[24][2] = 662;
		lines[24][3] = 332 + offset;

		lines[25][0] = 657;
		lines[25][1] = 290 + offset;
		lines[25][2] = 688;
		lines[25][3] = 328 + offset;

		// New line

		lines[26][0] = 118;
		lines[26][1] = 405 + offset;
		lines[26][2] = 100;
		lines[26][3] = 478 + offset;

		lines[27][0] = 174;
		lines[27][1] = 408 + offset;
		lines[27][2] = 163;
		lines[27][3] = 478 + offset;

		lines[28][0] = 228;
		lines[28][1] = 412 + offset;
		lines[28][2] = 224;
		lines[28][3] = 478 + offset;

		lines[29][0] = 283;
		lines[29][1] = 414 + offset;
		lines[29][2] = 288;
		lines[29][3] = 478 + offset;

		lines[30][0] = 340;
		lines[30][1] = 413 + offset;
		lines[30][2] = 353;
		lines[30][3] = 478 + offset;

		lines[31][0] = 394;
		lines[31][1] = 413 + offset;
		lines[31][2] = 413;
		lines[31][3] = 478 + offset;
		// End
	}

	/**
	 * Identify where divisor lines are in current lot view.
	 * 
	 * @return an array of coordinate pairs that represents the pixel location
	 *         of parking spots divisor lines
	 */
	public int[][] getSpotMatrix() {
		return lines;
	}

}

// end ProcessigManager

