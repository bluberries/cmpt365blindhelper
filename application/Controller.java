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
import javafx.scene.control.Button;
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
	private boolean toggle = true;
	int i = 0;
	
	private Mat STI = new Mat();
	
	@FXML
	Button Toggle;
	
	@FXML
	private Slider slider;
	
	@FXML
	private Slider volumeSlider;
	
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
			        Mat frame2 = new Mat();
	    			if (play == true && capture.read(frame)) { // decode successfully
			            double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
//			            System.out.println(totalFrameCount);
			            STI.create(frame.rows(), (int) totalFrameCount, frame.type());
	    				if(toggle == true) { //create STI by copying pixels
	    					frame.col(frame.cols()/2).copyTo(STI.col(i));
		    				i = i+1;
		    				Image im = Utilities.mat2Image(STI);
		    				Utilities.onFXThread(imageView.imageProperty(), im); 
	    				} else if(capture.read(frame2)){ //create STI by histogram
	    					calcHist(frame);
	    					calcHist(frame2);
		    				Image im = Utilities.mat2Image(STI);
		    				Utilities.onFXThread(imageView.imageProperty(), im); 
	    				}
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
//		System.out.println(i);
		play = !play;
	}
	
	@FXML
	protected void toggleImage(ActionEvent event) {
		toggle = !toggle;
		if(toggle == true) {
			Toggle.setText("Copy");
		} else {
			Toggle.setText("Hist");
		}
	}
	
	//calculates the chromaticity of each pixel in the frame then creates and returns a 2D histogram
	//input should be a mat object with number of columns == 1
	protected float[][] calcHist(Mat mat) {
		int N = (int) (1 + (Math.log(mat.rows())/Math.log(2)));
		int sum = 0;
//		System.out.println("number of bins == " + N);
		float[][] hist = new float[N][N];
		for(int i=0; i<N; i++) {
			for(int j=0; j<N; j++) {
				hist[i][j] = 0;
			}
		}
		for(int i=0; i<mat.rows(); i++) {
			for(int j=0; j<mat.cols(); j++) {
				double[] pixel  = mat.get(i, j);
				if(pixel[0] != 0 && pixel[1] != 0 && pixel[2] != 0) { //making sure we don't divide by 0
					double R = pixel[0]/(pixel[0] + pixel[1] + pixel[2]);
					double G = pixel[1]/(pixel[0] + pixel[1] + pixel[2]);
					
					//dividing pixels into the right bin for histogram
					int r = (int) Math.round(R * N);
					int g = (int) Math.round(G * N);
					hist[r][g] = hist[r][g] + 1;
					sum++;
				} else {
					//keep it at 0,0
					int r = 0;
					int g = 0;
					hist[r][g] = hist[r][g] + 1;
					sum++;
				}
			}
		}
		for(int i=0; i<N; i++) {
			for(int j=0; j<N; j++) {
				hist[i][j] = hist[i][j]/sum;
			}
		}
//		for(int i=0; i<N; i++) {
//			for(int j=0; j<N; j++) {
//				System.out.print(hist[i][j] + "     ");
//			}
//			System.out.println();
//		}
		return hist;
	}
}
