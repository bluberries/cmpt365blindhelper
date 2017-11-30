package application;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.opencv.imgproc.Imgproc;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import utilities.Utilities;
public class Controller {
	
	
	@FXML
	private ImageView imageView; // the image display window in the GUI
	@FXML
	private ImageView imageView1; // second display in the GUI
	
	private Mat image;
	
	private boolean play = false;
	private boolean togThresh = true;
	int i = 0;
	
	private Mat STICopy = new Mat();
	private Mat STIHist = new Mat();
	private Mat STIThre = new Mat();
	
	@FXML
	Button pause;
	@FXML
	Button toggle;
	
	
	private VideoCapture capture;
	private ScheduledExecutorService timer;
	
    private Mat frame = new Mat();
    private Mat prevFrame = new Mat();
	@FXML
	private void initialize() {
		// Optional: You should modify the logic so that the user can change these values
		// You may also do some experiments with different values
		
//		Mat object = new Mat(2,2, 2);
//		System.out.println(object.dump());
		
		
		
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
	
	//Grabs a frame (or two) from the video and creates two STIs; one from copying pixels and one from creating histograms
	//For STIHist, it only begins once the second frame has been grabbed
	protected void createFrameGrabber() throws InterruptedException {
		if (capture != null && capture.isOpened()) { // the video must be open
			double framePerSecond = capture.get(Videoio.CAP_PROP_FPS);
		    // create a runnable to fetch new frames periodically
		    Runnable frameGrabber = new Runnable() {
		    	@Override
		        public void run() {
//			        Mat frame = new Mat();
//			        Mat prevFrame = new Mat();
	    			if (play == true) { 
	    				if (capture.read(frame)) {
				            double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
				            STICopy.create(frame.rows(), (int) totalFrameCount, frame.type());
				            STIHist.create(frame.cols(), (int) totalFrameCount, frame.type());
				            STIThre.create(frame.cols(), (int) totalFrameCount, frame.type());
	    					frame.col(frame.cols()/2).copyTo(STICopy.col(i));
		    				i = i+1;
		    				Image im = Utilities.mat2Image(STICopy);
		    				Utilities.onFXThread(imageView.imageProperty(), im); 
		    				if (capture.get(Videoio.CAP_PROP_POS_FRAMES) > 1 && capture.get(Videoio.CAP_PROP_POS_FRAMES) < totalFrameCount) {
		    					//Calculating Scalar I
		    					Mat column = difMat(prevFrame, frame);
		    					Mat threshold = thresh(column);
		    					//Stitching together the STI
		    					column.col(0).copyTo(STIHist.col(i));
		    					threshold.col(0).copyTo(STIThre.col(i));
			    				Image im2 = Utilities.mat2Image(STIHist);
			    				Image im3 = Utilities.mat2Image(STIThre);
//			    				System.out.println("Putting image on Thread");
			    				if(togThresh) {
			    					Utilities.onFXThread(imageView1.imageProperty(), im2);
			    				} else {
			    					Utilities.onFXThread(imageView1.imageProperty(), im3);
			    				}
		    				}
		    				frame.copyTo(prevFrame);
		    				}
	    				else { // reach the end of the video
		    				play = false;
		    				i = 0;
		    				timer.shutdownNow();
	    				}
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
		
	protected Mat copyHelper(Mat source) {
		Mat destination = new Mat(source.rows(), source.cols(), source.type());
		for(int i=0; i<source.cols(); i++) {
			source.col(i).copyTo(destination.col(i));
		}
		return source;
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
			imageView1.setImage(Utilities.mat2Image(image));
		}
	}

	
	@FXML
	protected void pauseImage(ActionEvent event) {
		play = !play;
		if(play) {
			pause.setText("Pause");
		} else {
			pause.setText("Resume");
		}
	}
	
	@FXML
	protected void toggleImage(ActionEvent event) {
		togThresh = !togThresh;
		if(togThresh) {
			toggle.setText("No Threshold");
		} else {
			toggle.setText("Yes Threshold");
		}
	}
	
	//calculates the chromaticity of each pixel in the frame then creates and returns a 2D histogram
	//input should be a mat object with number of columns == 1
	protected Mat calcHist(Mat mat) {
		int N = (int) (1 + (Math.log(mat.rows())/Math.log(2)));
		int sum = 0;
		Mat hist = new Mat(N, N, CvType.CV_32F);
		for(int i=0; i<N; i++) {
			for(int j=0; j<N; j++) {
				hist.put(i, j, 0);
			}
		}
		for(int i=0; i<mat.rows(); i++) {
			for(int j=0; j<mat.cols(); j++) {
				double[] pixel  = mat.get(i, j);
				if(pixel[0] != 0 && pixel[1] != 0 && pixel[2] != 0) { //making sure we don't divide by 0
					double R = pixel[0]/(pixel[0] + pixel[1] + pixel[2]);
					double G = pixel[1]/(pixel[0] + pixel[1] + pixel[2]);
					//dividing pixels into the right bin for histogram
					int r = (int) Math.floor(R * N);
					int g = (int) Math.floor(G * N);
					hist.put(r, g, hist.get(r,g)[0] + 1);
					sum++;
				} else {
					//keep it at 0,0
					int r = 0;
					int g = 0;
					hist.put(r, g, hist.get(r,g)[0] + 1);
					sum++;
				}
			}
		}
		for(int i=0; i<N; i++) {
			for(int j=0; j<N; j++) {
				hist.put(i, j, hist.get(i,j)[0]/sum);
			}
		}
		return hist;
	}
	
	//Calculates value of I from two histograms
	protected double difHist(Mat hist1, Mat hist2) {
		return Imgproc.compareHist(hist1, hist2, Imgproc.CV_COMP_INTERSECT);
	}
	
	//Creates a Mat of scalar I values from 2 frames
	//The Mat has number of rows = frame's number of columns
	//and the number of columns = 1
	protected Mat difMat(Mat prevFrame, Mat currFrame) {
		int cols = currFrame.cols();
		double[] difHist = new double[cols];
		Mat result = new Mat(cols, 1, CvType.CV_8UC3);
		for(int i=0; i<cols; i++) {
			difHist[i] = difHist(calcHist(prevFrame.col(i)), calcHist(currFrame.col(i)));
		}
		//Rescaling colours and placing them into column Mat
		for(int i=0; i<cols; i++) {
			double color = Math.round(difHist[i] * 255);
			result.put(i, 0, color, color, color);
		}
		return result;
	}
	
	//If the I value of pixel is > 0.7 it's set to 1, otherwise set to 0
	//Creates a cleaner STI that makes it easier to see transitions
	protected Mat thresh(Mat column) {
		Mat result = new Mat(column.rows(), column.cols(), column.type());
		for(int i=0; i<result.rows(); i++) {
			if(column.get(i, 0)[0] > 178) {
				result.put(i, 0, 255, 255, 255);
			} else {
				result.put(i, 0, 0, 0, 0);
			}
		}
		return result;
	}
}
