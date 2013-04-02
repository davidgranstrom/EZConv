// ===========================================================================
// Title       : EZConv
// Description : Wrapper around PartConv. Adapted from the PartConv helpfile.
// Author      : David Granstrom 2013
// ===========================================================================

EZConv {

    var path, fftSize, server, <irSpectrum;

    *new {|path, fftSize=4096, server|
        ^super.newCopyArgs(path, fftSize, server).init;
    }

    /*
    * prepare the impulse response to be used with PartConv
    */
    init {
        try { path } ?? { "Supply a path for the IR!".throw };
        forkIfNeeded {
            var ir, irBuffer, bufSize = List[];
            var numChannels = SoundFile.use(path, {|f| f.numChannels });
            server = server ? Server.default;
            server.sync;
            // mono buffer only
            irBuffer = numChannels.collect{|i|
                Buffer.readChannel(server, path, channels: i)
            };
            server.sync;
            // get the size
            irBuffer.do{|buf|
                bufSize.add(PartConv.calcBufSize(fftSize, buf));
            };
            server.sync;
            irSpectrum = numChannels.collect{|i| Buffer.alloc(server, bufSize[i], 1) };
            server.sync;
            irBuffer.do{|buf, i|
                irSpectrum[i].preparePartConv(buf, fftSize);
            };
            server.sync;
            // don't need time domain data anymore, just needed spectral version
            irBuffer.do{|buf| buf.free };
        }
    }

    prepareNRT {|renderPath="/tmp", headerFormat="wav", sampleFormat="int24"|
        var name = PathName(path).fileNameWithoutExtension;
        irSpectrum.do{|buf, i|
            var p = renderPath +/+ name ++ "_NRT_" ++ i ++ "." ++ headerFormat;
            buf.write(p, headerFormat, sampleFormat,
                completionMessage: {|b|
                    "Wrote buffer: % to disk as: %".format(b, p).postln;
                }
            );
        };
    }

    /*
    * pseudo *ar (instance) method, to be used with SynthDefs
    */
    ar {|in, leak=0, mul=1|
        in = if(in.numChannels==1) { [ in ] } { in + (in.mean * leak) };
        ^[ in, irSpectrum ].flop.collect{|x| PartConv.ar(x[0], fftSize, x[1], mul) };
    }

    numChannels {
        ^irSpectrum.size;
    }

    free {
        irSpectrum.do{|buf| buf.free }
    }
}
