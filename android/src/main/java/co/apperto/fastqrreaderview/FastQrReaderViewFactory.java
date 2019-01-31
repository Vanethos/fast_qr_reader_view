package co.apperto.fastqrreaderview;

import android.content.Context;

import io.flutter.plugin.platform.PlatformViewFactory;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.StandardMessageCodec;
import io.flutter.plugin.platform.PlatformView;

/**
 * Factory for the FastQrReaderView
 */
public class FastQrReaderViewFactory extends PlatformViewFactory {
    private PluginRegistry.Registrar registrar;

    public FastQrReaderViewFactory(PluginRegistry.Registrar registrar) {
        super(StandardMessageCodec.INSTANCE);
        this.registrar = registrar;
    }

    @Override
    public PlatformView create(Context context, int i, Object o) {
        return new FastQrReaderView(context, registrar);
    }
}
