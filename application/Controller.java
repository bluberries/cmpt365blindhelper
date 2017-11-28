package application;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import utilities.Utilities;

public class Controller {
	
	
	@FXML
	private ImageView imageView; // the image display window in the GUI
	
	private Mat image;
	
	private boolean play = false;
	int i = 0;
	
	private Mat STI = new Mat();
	
	@FXML
	private Slider slider;
	
	private VideoCapture capture;
	private ScheduledExecutorService timer;
	
	@FXML
	private void initialize() {
		// Optional: You should modify the logic so that the user can change these values
		// You may also do some experiments with different values
		
		
		
		
	}
	
	private String getImageFilename() {
		// This method should return the filename of the image to be played
		// You should insert your code here to allow user to select the file
		FileChooser fileChooser = new FileChooser();
		File file = fileChooser.showOpenDialog(null);
		String fileName = file.getAbsolutePath();
		System.out.println(fileName);
		return fileName;
//		return "resources/test.mp4";
	}
	
	protected void createFrameGrabber() throws InterruptedException {
		if (capture != null && capture.isOpened()) { // the video must be open
			double framePerSecond = capture.get(Videoio.CAP_PROP_FPS);
		    // create a runnable to fetch new frames periodically
		    Runnable frameGrabber = new Runnable() {
		    	@Override
		        public void run() {
			        Mat frame = new Mat();
	    			if (capture.read(frame) && play == true) { // decode successfully
			            double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
			            double currentFrameNumber = capture.get(Videoio.CAP_PROP_POS_FRAMES);
//			            System.out.println(totalFrameCount);
			            STI.create(frame.rows(), (int) totalFrameCount, frame.type());
	    				frame.col(frame.cols()/2).copyTo(STI.col(i));
	    				i = i+1;
	    				Image im = Utilities.mat2Image(STI);
	    				Utilities.onFXThread(imageView.imageProperty(), im); 
	    				slider.setValue(currentFrameNumber / totalFrameCount * (slider.getMax() - slider.getMin()));
	    			} else if (!capture.read(frame)) { // reach the end of the video
	    				play = false;
	    				i = 0;
	    				capture.set(Videoio.CAP_PROP_POS_FRAMES, 0);
	    			} else { // video paused
	    				//do nothing
	    			}
		    	}
		    };
			// terminate the timer if it is running 
			if (timer != null && !timer.isShutdown()) {
				timer.shutdown();
				timer.awaitTermination(Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
			}
				
			// run the frame grabber
			timer = Executors.newSingleThreadScheduledExecutor();
			timer.scheduleAtFixedRate(frameGrabber, 0, Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
		}
	}
		

	
	@FXML
	protected void openImage(ActionEvent event) throws InterruptedException {
		// This method opens an image and display it using the GUI
		// You should modify the logic so that it opens and displays a video
		String fileName = getImageFilename();
		capture = new VideoCapture(fileName); // open video file
		if (capture.isOpened()) { // open successfully
			System.out.println("Opened successfully");
			i = 0;
			play=true;
			createFrameGrabber();
		}
		else {
			image = Imgcodecs.imread(fileName);
			imageView.setImage(Utilities.mat2Image(image));
		}
	}

	
	@FXML
	protected void stopImage(ActionEvent event) {
		System.out.println(i);
		play = !play;
	}
}
