var exec = require('cordova/exec');

module.exports = {
    // Action 1: Initialize (Hanya cek izin dan siapkan ML Kit)
    initialize: function (successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'LivenessPlugin', 'initialize', []);
    },

    // Action 2: Start Liveness (Buka kamera dengan 4 parameter)
    startLiveness: function (options, successCallback, errorCallback) {
        // Membaca nilai dari OutSystems, default adalah false jika kosong
        var checkBlink = options.checkBlink || false;
        var checkNod   = options.checkNod || false;
        var checkShake = options.checkShake || false;
        var checkSmile = options.checkSmile || false;

        // Mengirim ke Java dalam bentuk Array agar ditangkap oleh args.optBoolean(0), dst.
        var args = [checkBlink, checkNod, checkShake, checkSmile];

        exec(successCallback, errorCallback, 'LivenessPlugin', 'startLiveness', args);
    }
};