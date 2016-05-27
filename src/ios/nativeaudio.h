//
//
//  NativeAudio.h
//  NativeAudio
//
//  Created by Sidney Bofah on 2014-06-26.
//

#import <Foundation/Foundation.h>
#import <Cordova/CDV.h>
#import <AVFoundation/AVFoundation.h>
#import <AVFoundation/AVAudioPlayer.h>
#import <AudioToolbox/AudioToolbox.h>
#import "NativeAudioAsset.h"

@interface NativeAudio : CDVPlugin <AVSpeechSynthesizerDelegate> {
    NSMutableDictionary* audioMapping;
    NSMutableDictionary* completeCallbacks;
    AVSpeechSynthesizer* synthesizer;
    NSString* lastCallbackId;
    NSString* callbackId;
    float speechAmplitude;
    NSUInteger lastLocation;
}

@property (strong, nonatomic) AVAudioPlayer *audioPlayer;

- (void) preloadSimple:(CDVInvokedUrlCommand *)command;
- (void) preloadComplex:(CDVInvokedUrlCommand *)command;
- (void) play:(CDVInvokedUrlCommand *)command;
- (void) stop:(CDVInvokedUrlCommand *)command;
- (void) loop:(CDVInvokedUrlCommand *)command;
- (void) unload:(CDVInvokedUrlCommand *)command;
- (void) setVolumeForComplexAsset:(CDVInvokedUrlCommand *)command;
- (void) addCompleteListener:(CDVInvokedUrlCommand *)command;
- (void) getCurrentAmplitude:(CDVInvokedUrlCommand *)command;
- (void) speak:(CDVInvokedUrlCommand*)command;
- (void) stopSpeak:(CDVInvokedUrlCommand*)command;

@end