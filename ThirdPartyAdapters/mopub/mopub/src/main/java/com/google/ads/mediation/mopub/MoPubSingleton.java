package com.google.ads.mediation.mopub;

import android.app.Activity;

import com.google.android.gms.ads.mediation.MediationAdConfiguration;
import com.mopub.common.MoPub;
import com.mopub.common.SdkConfiguration;
import com.mopub.common.SdkInitializationListener;
import com.mopub.common.logging.MoPubLog;
import com.mopub.mobileads.dfp.adapters.MoPubAdapter;

import java.util.ArrayList;

public class MoPubSingleton {

    private static MoPubSingleton instance;
    private static boolean isInitializing;

    private ArrayList<SdkInitializationListener> mInitListeners = new ArrayList<>();

    public static MoPubSingleton getInstance() {
        if (instance == null) {
            instance = new MoPubSingleton();
        }
        return instance;
    }

    public void initializeMoPubSDK(Activity activity,
                                   SdkConfiguration configuration,
                                   SdkInitializationListener listener) {
        if (MoPub.isSdkInitialized()) {
            listener.onInitializationFinished();
            return;
        }

        mInitListeners.add(listener);
        if (!isInitializing) {
            isInitializing = true;

            MoPub.initializeSdk(activity, configuration, new SdkInitializationListener() {
                @Override
                public void onInitializationFinished() {
                    MoPubLog.d("MoPub SDK initialized.");

                    for (SdkInitializationListener initListener : mInitListeners) {
                        initListener.onInitializationFinished();
                    }
                    mInitListeners.clear();
                }
            });
        }
    }

    static String getKeywords(MediationAdConfiguration mediationConfiguration,
                              boolean intendedForPII) {
        if (intendedForPII) {
            if (MoPub.canCollectPersonalInformation()) {
                return containsPII(mediationConfiguration) ?
                        MoPubAdapter.MOPUB_NATIVE_CEVENT_VERSION : "";
            } else {
                return "";
            }
        } else {
            return containsPII(mediationConfiguration) ? "" :
                    MoPubAdapter.MOPUB_NATIVE_CEVENT_VERSION;
        }
    }

    static boolean containsPII(MediationAdConfiguration configuration) {
        return configuration.getLocation() != null;
    }
}
