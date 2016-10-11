package matlab_interface;

import javafx.application.Application;
import javafx.stage.Stage;
import ui.DisplayUI;
import ui.ProcessingManager;

public class MatlabIf  extends Application {
	
	//Make the two main objects. The UI and the background processor
	private ProcessingManager pm;
	public DisplayUI ui;
	
	//The object that Matlab will share with java
	public volatile boolean[] sharedData;
	
	public MatlabIf() {
		pm = new ProcessingManager(20,false, sharedData);
		
		//begin the two main object threads
		pm.beginProcThread();
		launch();
	}
	
	public void updateSpots(boolean[] spots){sharedData=spots;}
	
	public boolean[] getSpots(){return sharedData;}


	/**
	 * Starts the UI display.
	 * 
	 * @param primaryStage
	 *            The stage containing the main application window
	 */
	public void start(Stage primaryStage) throws Exception {
		//part of the UI thread. Initializes the UI with a reference to the processor,
		//the gives the processor a reference to the UI. This allows communication
		//between the two main threads.
		ui = new DisplayUI(pm);
		ui.start(primaryStage);
		pm.setUIRef(ui);
	}
	

}
