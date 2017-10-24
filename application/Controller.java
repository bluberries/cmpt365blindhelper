package application;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
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
	
	private int width;
	private int height;
	private int sampleRate; // sampling frequency
	private int sampleSizeInBits;
	private int numberOfChannels;
	private double[] freq; // frequencies for each particular row
	private int numberOfQuantizionLevels;
	private int numberOfSamplesPerColumn;
	
	@FXML
	private Slider slider;
	
	@FXML
	private Slider volumeSlider;
	
	private VideoCapture capture;
	private ScheduledExecutorService timer;
	private ScheduledExecutorService audioTimer;
	
	@FXML
	private void initialize() {
		// Optional: You should modify the logic so that the user can change these values
		// You may also do some experiments with different values
		
		width = 16;
		height = 16;
		sampleRate = 8000;
		sampleSizeInBits = 8;
		numberOfChannels = 1;
		
		numberOfQuantizionLevels = 16;
		
		numberOfSamplesPerColumn = 500;
		
		// assign frequencies for each particular row
		freq = new double[height]; // Be sure you understand why it is height rather than width
		freq[height/2-1] = 440.0; // 440KHz - Sound of A (La)
		for (int m = height/2; m < height; m++) {
			freq[m] = freq[m-1] * Math.pow(2, 1.0/12.0); 
		}
		for (int m = height/2-2; m >=0; m--) {
			freq[m] = freq[m+1] * Math.pow(2, -1.0/12.0); 
		}
		
		volumeSlider.setValue(volumeSlider.getMax());
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
		    		synchronized(capture) {
		    			try {
//		    				System.out.println("Inside run");
				    		Mat frame = new Mat();
			    			if (capture.read(frame)) { // decode successfully
			    				Image im = Utilities.mat2Image(frame);
			    				Utilities.onFXThread(imageView.imageProperty(), im); 
			    				double currentFrameNumber = capture.get(Videoio.CAP_PROP_POS_FRAMES);
			    				double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
			    				slider.setValue(currentFrameNumber / totalFrameCount * (slider.getMax() - slider.getMin()));
			    			} else { // reach the end of the video
			    				capture.set(Videoio.CAP_PROP_POS_FRAMES, 0);
			    			}
			    			capture.wait();
		    			} catch (InterruptedException e) {
		    				e.printStackTrace();
		    			}
		    		}
		    	}
			};
			slider.valueProperty().addListener(new InvalidationListener() {
			    public void invalidated(Observable ov) {
			       if (slider.isValueChanging()) {
			    	   capture.set(Videoio.CAP_PROP_POS_FRAMES, (int) (slider.getValue() / 100 * capture.get(Videoio.CAP_PROP_FRAME_COUNT)));
			    	   timer.schedule(frameGrabber, 0, TimeUnit.MILLISECONDS);
			       }
			    }
			});
			
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
			createFrameGrabber();
		}
		else {
			image = Imgcodecs.imread(fileName);
			imageView.setImage(Utilities.mat2Image(image));
		}
		// You don't have to understand how mat2Image() works. 
		// In short, it converts the image from the Mat format to the Image format
		// The Mat format is used by the opencv library, and the Image format is used by JavaFX
		// BTW, you should be able to explain briefly what opencv and JavaFX are after finishing this assignment
	}

	@FXML
	protected void playImage(ActionEvent event) throws LineUnavailableException {
		// This method "plays" the image opened by the user
		// You should modify the logic so that it plays a video rather than an image
		Mat frame = new Mat();
		Runnable audioGrabber = new Runnable() {
			@Override
			public void run() {
				while (capture.read(frame)) {
		            synchronized (capture) {
		            	capture.notify();
		            }
					// convert the image from RGB to grayscale
					Mat grayImage = new Mat();
					Imgproc.cvtColor(frame, grayImage, Imgproc.COLOR_BGR2GRAY);
					
					// resize the image
					Mat resizedImage = new Mat();
					Imgproc.resize(grayImage, resizedImage, new Size(width, height));
					
					// quantization
					double[][] roundedImage = new double[resizedImage.rows()][resizedImage.cols()];
					for (int row = 0; row < resizedImage.rows(); row++) {
						for (int col = 0; col < resizedImage.cols(); col++) {
							roundedImage[row][col] = (double)Math.floor(resizedImage.get(row, col)[0]/numberOfQuantizionLevels) / numberOfQuantizionLevels;
						}
					}
					
					// I used an AudioFormat object and a SourceDataLine object to perform audio output. Feel free to try other options
			        try {
			        	AudioFormat audioFormat = new AudioFormat(sampleRate, sampleSizeInBits, numberOfChannels, true, true);
			            SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
			            sourceDataLine.open(audioFormat, sampleRate);
			            sourceDataLine.start();
			            
			            FloatControl gainControl=(FloatControl) sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN);
				    	gainControl.setValue((float)(Math.log( (int) volumeSlider.getValue() / 100d) / Math.log(10.0) * 20.0));	// volume is always checked before audio plays
			        	/* this piece of code to transform linear 0-100 values to decibels  was borrowed from http://www.javased.com/?api=javax.sound.sampled.SourceDataLine */
				    	
			            for (int col = 0; col < width; col++) {
			            	byte[] audioBuffer = new byte[numberOfSamplesPerColumn];
			            	for (int t = 1; t <= numberOfSamplesPerColumn; t++) {
			            		double signal = 0;
			                	for (int row = 0; row < height; row++) {
			                		int m = height - row - 1; // Be sure you understand why it is height rather width, and why we subtract 1 
			                		int time = t + col * numberOfSamplesPerColumn;
			                		double ss = Math.sin(2 * Math.PI * freq[m] * (double)time/sampleRate);
			                		signal += roundedImage[row][col] * ss;
			                	}
			                	double normalizedSignal = signal / height; // signal: [-height, height];  normalizedSignal: [-1, 1]
			                	audioBuffer[t-1] = (byte) (normalizedSignal*0x7F); // Be sure you understand what the weird number 0x7F is for
			            	}
			            	sourceDataLine.write(audioBuffer, 0, numberOfSamplesPerColumn);
			            }
			            byte[] click = new byte[numberOfSamplesPerColumn];
			            for(int i=0; i < 250; i++) {
			            	click[2*i] = (byte) 0x56;
			            	click[2*i+1] = (byte) 0x55;
			            }
			            sourceDataLine.write(click, 0, 20);
			            sourceDataLine.drain();
			            sourceDataLine.close();
			        } catch (LineUnavailableException e) {
			        	e.printStackTrace();
			        }
				} 
				if (image != null){
					// convert the image from RGB to grayscale
					Mat grayImage = new Mat();
					Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);
					
					// resize the image
					Mat resizedImage = new Mat();
					Imgproc.resize(grayImage, resizedImage, new Size(width, height));
					
					// quantization
					double[][] roundedImage = new double[resizedImage.rows()][resizedImage.cols()];
					for (int row = 0; row < resizedImage.rows(); row++) {
						for (int col = 0; col < resizedImage.cols(); col++) {
							roundedImage[row][col] = (double)Math.floor(resizedImage.get(row, col)[0]/numberOfQuantizionLevels) / numberOfQuantizionLevels;
						}
					}
					
					// I used an AudioFormat object and a SourceDataLine object to perform audio output. Feel free to try other options
					try {
						AudioFormat audioFormat = new AudioFormat(sampleRate, sampleSizeInBits, numberOfChannels, true, true);
						SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
						sourceDataLine.open(audioFormat, sampleRate);
						sourceDataLine.start();
						
						FloatControl gainControl=(FloatControl) sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN);
				    	gainControl.setValue((float)(Math.log( (int) volumeSlider.getValue() / 100d) / Math.log(10.0) * 20.0));
						            
						for (int col = 0; col < width; col++) {
							byte[] audioBuffer = new byte[numberOfSamplesPerColumn];
						    for (int t = 1; t <= numberOfSamplesPerColumn; t++) {
						    	double signal = 0;
						    	for (int row = 0; row < height; row++) {
						    		int m = height - row - 1; // Be sure you understand why it is height rather width, and why we subtract 1 
						    		int time = t + col * numberOfSamplesPerColumn;
						    		double ss = Math.sin(2 * Math.PI * freq[m] * (double)time/sampleRate);
						    		signal += roundedImage[row][col] * ss;
						    	}
						    	double normalizedSignal = signal / height; // signal: [-height, height];  normalizedSignal: [-1, 1]
						    	audioBuffer[t-1] = (byte) (normalizedSignal*0x7F); // Be sure you understand what the weird number 0x7F is for
						    }
						    sourceDataLine.write(audioBuffer, 0, numberOfSamplesPerColumn);
						}
						byte[] click = new byte[numberOfSamplesPerColumn];
						for(int i=0; i < 250; i++) {
							click[2*i] = (byte) 0x56;
							click[2*i+1] = (byte) 0x55;
						}
						sourceDataLine.write(click, 0, 20);
						sourceDataLine.drain();
						sourceDataLine.close();
					} catch (LineUnavailableException e) {
						e.printStackTrace();
					}
					}
			}
		};
		audioTimer = Executors.newSingleThreadScheduledExecutor();
		audioTimer.execute(audioGrabber);
	} 
}
