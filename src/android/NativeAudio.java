//
//
//  NativeAudio.java
//
//  Created by Sidney Bofah on 2014-06-26.
//

package com.rjfun.cordova.plugin.nativeaudio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.Callable;

import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;
import android.view.KeyEvent;
import android.media.MediaRecorder;
import android.media.audiofx.Visualizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.UtteranceProgressListener;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONObject;

public class NativeAudio extends CordovaPlugin implements AudioManager.OnAudioFocusChangeListener, OnInitListener {

    /* options */
    public static final String OPT_FADE_MUSIC = "fadeMusic";

	public static final String ERROR_NO_AUDIOID="A reference does not exist for the specified audio id.";
	public static final String ERROR_AUDIOID_EXISTS="A reference already exists for the specified audio id.";
	public static final String ERR_INVALID_OPTIONS = "ERR_INVALID_OPTIONS";
    public static final String ERR_NOT_INITIALIZED = "ERR_NOT_INITIALIZED";
    public static final String ERR_ERROR_INITIALIZING = "ERR_ERROR_INITIALIZING";
    public static final String ERR_UNKNOWN = "ERR_UNKNOWN";
	public static final String SET_OPTIONS="setOptions";
	public static final String PRELOAD_SIMPLE="preloadSimple";
	public static final String PRELOAD_COMPLEX="preloadComplex";
	public static final String PLAY="play";
	public static final String STOP="stop";
	public static final String LOOP="loop";
	public static final String UNLOAD="unload";
    public static final String ADD_COMPLETE_LISTENER="addCompleteListener";
	public static final String SET_VOLUME_FOR_COMPLEX_ASSET="setVolumeForComplexAsset";
	public static final String GET_CURRENT_AMPLITUDE="getCurrentAmplitude";
	public static final String SPEAK="speak";
	public static final String STOP_SPEAK="stopSpeak";

	private static final String LOGTAG = "NativeAudio";
	
	private static HashMap<String, NativeAudioAsset> assetMap;
    private static ArrayList<NativeAudioAsset> resumeList;
    private static HashMap<String, CallbackContext> completeCallbacks;
    private boolean fadeMusic = false;

    public void setOptions(JSONObject options) {
		if(options != null) {
			if(options.has(OPT_FADE_MUSIC)) this.fadeMusic = options.optBoolean(OPT_FADE_MUSIC);
		}
	}

    private static MediaRecorder mRecorder;
    private static Visualizer audioOutput;

    private boolean ttsInitialized = false;
    private TextToSpeech tts = null;

	private PluginResult executePreload(JSONArray data) {
		String audioID;
		try {
			audioID = data.getString(0);
			if (!assetMap.containsKey(audioID)) {
				String assetPath = data.getString(1);
				Log.d(LOGTAG, "preloadComplex - " + audioID + ": " + assetPath);
				
				double volume;
				if (data.length() <= 2) {
					volume = 1.0;
				} else {
					volume = data.getDouble(2);
				}

				int voices;
				if (data.length() <= 3) {
					voices = 1;
				} else {
					voices = data.getInt(3);
				}

				NativeAudioAsset asset = new NativeAudioAsset(
						assetPath, voices, (float)volume, cordova);
				assetMap.put(audioID, asset);

				return new PluginResult(Status.OK);
			} else {
				return new PluginResult(Status.ERROR, ERROR_AUDIOID_EXISTS);
			}
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		} catch (IOException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}		
	}
	
	private PluginResult executePlayOrLoop(String action, JSONArray data) {
		final String audioID;
		try {
			audioID = data.getString(0);
			//Log.d( LOGTAG, "play - " + audioID );

			if (assetMap.containsKey(audioID)) {
				NativeAudioAsset asset = assetMap.get(audioID);
				if (LOOP.equals(action))
					asset.loop();
				else
					asset.play(new Callable<Void>() {
                        public Void call() throws Exception {
                            if (completeCallbacks != null) {
                                CallbackContext callbackContext = completeCallbacks.get(audioID);
                                if (callbackContext != null) {
                                    JSONObject done = new JSONObject();
                                    done.put("id", audioID);
                                    callbackContext.sendPluginResult(new PluginResult(Status.OK, done));
                                }
                            }
                            return null;
                        }
					});                
			} else {
				return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		} catch (IOException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}
		
		return new PluginResult(Status.OK);
	}

	private PluginResult executeStop(JSONArray data) {
		String audioID;
		try {
			audioID = data.getString(0);
			//Log.d( LOGTAG, "stop - " + audioID );
			
			if (assetMap.containsKey(audioID)) {
				NativeAudioAsset asset = assetMap.get(audioID);
				asset.stop();
			} else {
				return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}			
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}
		
		return new PluginResult(Status.OK);
	}

	private PluginResult executeUnload(JSONArray data) {
		String audioID;
		try {
			audioID = data.getString(0);
			Log.d( LOGTAG, "unload - " + audioID );
			
			if (assetMap.containsKey(audioID)) {
				NativeAudioAsset asset = assetMap.get(audioID);
				asset.unload();
				assetMap.remove(audioID);
			} else {
				return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		} catch (IOException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}
		
		return new PluginResult(Status.OK);
	}

	private PluginResult executeSetVolumeForComplexAsset(JSONArray data) {
		String audioID;
		float volume;
		try {
			audioID = data.getString(0);
			volume = (float) data.getDouble(1);
			Log.d( LOGTAG, "setVolume - " + audioID );
			
			if (assetMap.containsKey(audioID)) {
				NativeAudioAsset asset = assetMap.get(audioID);
				asset.setVolume(volume);
			} else {
				return new PluginResult(Status.ERROR, ERROR_NO_AUDIOID);
			}
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}
		return new PluginResult(Status.OK);
	}

	private PluginResult executeGetCurrentAmplitude(JSONArray data) {
		// use MediaRecorder
		// 
		// String delay;
		// try {
		// 	delay = data.getString(0);
		// 	int ampl = mRecorder.getMaxAmplitude();
		// 	try{Thread.sleep(Integer.parseInt(delay));}catch(InterruptedException ie){ie.printStackTrace();}
		// 	int ampl2 = mRecorder.getMaxAmplitude();

		//    	return new PluginResult(Status.OK, String.valueOf(ampl2));
		// } catch (JSONException e) {
		// 	return new PluginResult(Status.ERROR, e.toString());
		// }

		// use Visualizer
		if(audioOutput == null) {
	        audioOutput = new Visualizer(0); // get output audio stream
	        audioOutput.setEnabled(true);
        }
        String delay;
		try {
		 	delay = data.getString(0);
        	try{Thread.sleep(Integer.parseInt(delay));}catch(InterruptedException ie){ie.printStackTrace();}
			byte[] bdata = new byte[audioOutput.getCaptureSize()];
	        audioOutput.getWaveForm(bdata);
	        int sum = 0;
	        for(int i=0;i<bdata.length;i++){
	            sum += bdata[i];
	       	}
	        double lvl = sum / bdata.length;
	        lvl += 128.0;

			lvl /= 128.0;
			if (lvl > 1.0)
				lvl = 1.0;
			lvl = Math.pow(lvl, 10);

			return new PluginResult(Status.OK, String.valueOf(lvl));
		} catch (JSONException e) {
			return new PluginResult(Status.ERROR, e.toString());
		}
	}

	private PluginResult executeSpeak(JSONArray data, String callbackId)
			throws JSONException, NullPointerException {
		JSONObject params = data.getJSONObject(0);

        if (params == null) {
            return new PluginResult(Status.ERROR, ERR_INVALID_OPTIONS);
        }

        String text;
        String locale;
        double rate;
        double pitch;

        if (params.isNull("text")) {
            return new PluginResult(Status.ERROR, ERR_INVALID_OPTIONS);
        } else {
            text = params.getString("text");
        }

        if (params.isNull("locale")) {
            locale = "en-US";
        } else {
            locale = params.getString("locale");
        }

        if (params.isNull("rate")) {
            rate = 1.0;
        } else {
            rate = params.getDouble("rate");
        }

        if (params.isNull("pitch")) {
            rate = 1.0;
        } else {
            rate = params.getDouble("pitch");
        }

        if (tts == null) {
            return new PluginResult(Status.ERROR, ERR_ERROR_INITIALIZING);
        }

        if (!ttsInitialized) {
            return new PluginResult(Status.ERROR, ERR_NOT_INITIALIZED);
        }

        HashMap<String, String> ttsParams = new HashMap<String, String>();
        ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, callbackId);

        String[] localeArgs = locale.split("-");
        tts.setLanguage(new Locale(localeArgs[0], localeArgs[1]));
        tts.setSpeechRate((float) rate);

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, ttsParams);
        return new PluginResult(Status.OK);
	}

	private PluginResult executeStopSpeak(JSONArray data)
	{

		if (tts == null) {
            return new PluginResult(Status.ERROR, ERR_ERROR_INITIALIZING);
        }

		tts.stop();
		return new PluginResult(Status.OK);
	}

	@Override
	protected void pluginInitialize() {
		AudioManager am = (AudioManager)cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);

	        int result = am.requestAudioFocus(this,
	                // Use the music stream.
	                AudioManager.STREAM_MUSIC,
	                // Request permanent focus.
	                AudioManager.AUDIOFOCUS_GAIN);

		// Allow android to receive the volume events
		this.webView.setButtonPlumbedToJs(KeyEvent.KEYCODE_VOLUME_DOWN, false);
		this.webView.setButtonPlumbedToJs(KeyEvent.KEYCODE_VOLUME_UP, false);

		// Text to speech addition
		tts = new TextToSpeech(cordova.getActivity().getApplicationContext(), this);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String s) {
                // do nothing
            }

            @Override
            public void onDone(String callbackId) {
                if (!callbackId.equals("")) {
                    CallbackContext context = new CallbackContext(callbackId, NativeAudio.this.webView);
                    context.success();
                }
            }

            @Override
            public void onError(String callbackId) {
                if (!callbackId.equals("")) {
                    CallbackContext context = new CallbackContext(callbackId, NativeAudio.this.webView);
                    context.error(ERR_UNKNOWN);
                }
            }
        });
		// mRecorder = new MediaRecorder();
  //       mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
  //       mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
  //       mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
  //       mRecorder.setOutputFile("/dev/null"); 
                         
  //       try {
  //           mRecorder.prepare();
  //       } catch (IllegalStateException e) {
  //           // TODO Auto-generated catch block
  //           e.printStackTrace();
  //       } catch (IOException e) {
  //           // TODO Auto-generated catch block
  //           e.printStackTrace();
  //       }
         
		// mRecorder.start();
	}

	@Override
	public boolean execute(final String action, final JSONArray data, final CallbackContext callbackContext) {
		Log.d(LOGTAG, "Plugin Called: " + action);
		
		PluginResult result = null;
		initSoundPool();
		
		try {
			if (SET_OPTIONS.equals(action)) {
                JSONObject options = data.optJSONObject(0);
                this.setOptions(options);
                callbackContext.sendPluginResult( new PluginResult(Status.OK) );

			} else if (PRELOAD_SIMPLE.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executePreload(data) );
		            }
		        });				
				
			} else if (PRELOAD_COMPLEX.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executePreload(data) );
		            }
		        });				

			} else if (PLAY.equals(action) || LOOP.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executePlayOrLoop(action, data) );
		            }
		        });				
				
			} else if (STOP.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
		            public void run() {
		            	callbackContext.sendPluginResult( executeStop(data) );
		            }
		        });

            } else if (UNLOAD.equals(action)) {
                cordova.getThreadPool().execute(new Runnable() {
                    public void run() {
                        executeStop(data);
                        callbackContext.sendPluginResult( executeUnload(data) );
                    }
                });
            } else if (ADD_COMPLETE_LISTENER.equals(action)) {
                if (completeCallbacks == null) {
                    completeCallbacks = new HashMap<String, CallbackContext>();
                }
                try {
                    String audioID = data.getString(0);
                    completeCallbacks.put(audioID, callbackContext);
                } catch (JSONException e) {
                    callbackContext.sendPluginResult(new PluginResult(Status.ERROR, e.toString()));
				}
	    	} else if (SET_VOLUME_FOR_COMPLEX_ASSET.equals(action)) {
				cordova.getThreadPool().execute(new Runnable() {
					public void run() {
	                    callbackContext.sendPluginResult( executeSetVolumeForComplexAsset(data) );
                    }
                });
	    	} else if(GET_CURRENT_AMPLITUDE.equals(action)) {
	    		cordova.getThreadPool().execute(new Runnable() {
					public void run() {
	                    callbackContext.sendPluginResult( executeGetCurrentAmplitude(data) );
                    }
                });
	    	} else if(SPEAK.equals(action)) {
	    		cordova.getThreadPool().execute(new Runnable() {
					public void run() {
						try {
	                    	callbackContext.sendPluginResult( executeSpeak(data, callbackContext.getCallbackId()) );
	                    }
	                    catch (JSONException e)
	                    {
	                    	Log.e(LOGTAG, e.toString());
	                    }
                    }
                });
	    	} else if(STOP_SPEAK.equals(action)) {
	    		cordova.getThreadPool().execute(new Runnable() {
					public void run() {
	                    callbackContext.sendPluginResult( executeStopSpeak(data) );
                    }
                });
	    	}
            else {
                result = new PluginResult(Status.OK);
            }
		} catch (Exception ex) {
			result = new PluginResult(Status.ERROR, ex.toString());
		}

		if(result != null) callbackContext.sendPluginResult( result );
		return true;
	}

	@Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            tts = null;
        } else {
            // warm up the tts engine with an empty string
            HashMap<String, String> ttsParams = new HashMap<String, String>();
            ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "");
            tts.setLanguage(new Locale("en", "US"));
            tts.speak("", TextToSpeech.QUEUE_FLUSH, ttsParams);

            ttsInitialized = true;
        }
    }

	private void initSoundPool() {

		if (assetMap == null) {
			assetMap = new HashMap<String, NativeAudioAsset>();
		}

        if (resumeList == null) {
            resumeList = new ArrayList<NativeAudioAsset>();
        }
	}

    public void onAudioFocusChange(int focusChange) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            // Pause playback
        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            // Resume playback
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            // Stop playback
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);

        for (HashMap.Entry<String, NativeAudioAsset> entry : assetMap.entrySet()) {
            NativeAudioAsset asset = entry.getValue();
            boolean wasPlaying = asset.pause();
            if (wasPlaying) {
                resumeList.add(asset);
            }
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        while (!resumeList.isEmpty()) {
            NativeAudioAsset asset = resumeList.remove(0);
            asset.resume();
        }
    }
}
