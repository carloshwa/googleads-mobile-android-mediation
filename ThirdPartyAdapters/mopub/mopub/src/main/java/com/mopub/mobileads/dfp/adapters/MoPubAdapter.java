package com.mopub.mobileads.dfp.adapters;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;

import com.google.ads.mediation.mopub.MoPubSingleton;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.mediation.MediationAdRequest;
import com.google.android.gms.ads.mediation.MediationBannerAdapter;
import com.google.android.gms.ads.mediation.MediationBannerListener;
import com.google.android.gms.ads.mediation.MediationInterstitialAdapter;
import com.google.android.gms.ads.mediation.MediationInterstitialListener;
import com.google.android.gms.ads.mediation.MediationNativeAdapter;
import com.google.android.gms.ads.mediation.MediationNativeListener;
import com.google.android.gms.ads.mediation.NativeMediationAdRequest;
import com.mopub.common.MoPub;
import com.mopub.common.SdkConfiguration;
import com.mopub.common.SdkInitializationListener;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubView;

import java.util.Calendar;
import java.util.Date;

import static com.google.android.gms.ads.AdRequest.GENDER_FEMALE;
import static com.google.android.gms.ads.AdRequest.GENDER_MALE;

/**
 * A {@link com.mopub.mobileads.dfp.adapters.MoPubAdapter} used to mediate banner ads from MoPub.
 */
public class MoPubAdapter implements MediationNativeAdapter, MediationBannerAdapter,
        MediationInterstitialAdapter {
    public static final String TAG = MoPubAdapter.class.getSimpleName();

    private MoPubView mMoPubView;
    private AdSize mAdSize;

    private static final String MOPUB_NATIVE_CEVENT_VERSION = "gmext";
    private static final String MOPUB_AD_UNIT_KEY = "adUnitId";

    @Override
    public void onDestroy() {
        if (mMoPubView != null) {
            mMoPubView.destroy();
            mMoPubView = null;
        }
    }

    @Override
    public void onPause() {
    }

    @Override
    public void onResume() {
    }

    @Override
    public void requestNativeAd(final Context context,
                                final MediationNativeListener listener,
                                Bundle serverParameters,
                                NativeMediationAdRequest mediationAdRequest,
                                Bundle mediationExtras) {
        // Stub
    }

    @Override
    public void requestBannerAd(Context context,
                                MediationBannerListener mediationBannerListener,
                                Bundle bundle,
                                AdSize adSize,
                                MediationAdRequest mediationAdRequest,
                                Bundle bundle1) {

        String adUnit = bundle.getString(MOPUB_AD_UNIT_KEY);
        if (TextUtils.isEmpty(adUnit)) {
            Log.d(TAG, "Missing or Invalid MoPub Ad Unit ID.");
            mediationBannerListener.onAdFailedToLoad(MoPubAdapter.this,
                    AdRequest.ERROR_CODE_INVALID_REQUEST);
            return;
        }

        mAdSize = getSupportedAdSize(context, adSize);
        if (mAdSize == null) {
            Log.w(TAG, "Failed to request ad, AdSize is null.");
            mediationBannerListener.onAdFailedToLoad(this, AdRequest.ERROR_CODE_INVALID_REQUEST);
            return;
        }

        mMoPubView = new MoPubView(context);
        mMoPubView.setBannerAdListener(new MBannerListener(mediationBannerListener));
        mMoPubView.setAdUnitId(adUnit);

        //If test mode is enabled
        if (mediationAdRequest.isTesting()) {
            mMoPubView.setTesting(true);
        }

        //If location is available
        if (mediationAdRequest.getLocation() != null) {
            mMoPubView.setLocation(mediationAdRequest.getLocation());
        }

        mMoPubView.setKeywords(getKeywords(mediationAdRequest, false));
        mMoPubView.setUserDataKeywords(getKeywords(mediationAdRequest, true));

        SdkConfiguration sdkConfiguration = new SdkConfiguration.Builder(adUnit).build();
        MoPubSingleton.getInstance().initializeMoPubSDK(context, sdkConfiguration,
                new SdkInitializationListener() {
            @Override
            public void onInitializationFinished() {
               mMoPubView.loadAd();
            }
        });
    }

    private AdSize getSupportedAdSize(Context context, AdSize adSize) {
        return AdSize.BANNER;
    }

    @Override
    public View getBannerView() {
        return mMoPubView;
    }

    /* Keywords passed from AdMob are separated into 1) personally identifiable, and 2) non-personally
    identifiable categories before they are forwarded to MoPub due to GDPR.
     */
    private static String getKeywords(MediationAdRequest mediationAdRequest, boolean intendedForPII) {

        Date birthday = mediationAdRequest.getBirthday();
        String ageString = "";

        if (birthday != null) {
            int ageInt = getAge(birthday);
            ageString = "m_age:" + Integer.toString(ageInt);
        }

        int gender = mediationAdRequest.getGender();
        String genderString = "";

        if (gender != -1) {
            if (gender == GENDER_FEMALE) {
                genderString = "m_gender:f";
            } else if (gender == GENDER_MALE) {
                genderString = "m_gender:m";
            }
        }

        StringBuilder keywordsBuilder = new StringBuilder();

        keywordsBuilder = keywordsBuilder.append(MOPUB_NATIVE_CEVENT_VERSION)
                .append(",").append(ageString)
                .append(",").append(genderString);

        if (intendedForPII) {
            if (MoPub.canCollectPersonalInformation()) {
                return keywordsContainPII(mediationAdRequest) ? keywordsBuilder.toString() : "";
            } else {
                return "";
            }
        } else {
            return keywordsContainPII(mediationAdRequest) ? "" : keywordsBuilder.toString();
        }
    }

    // Check whether passed keywords contain personally-identifiable information
    private static boolean keywordsContainPII(MediationAdRequest mediationAdRequest) {
        return mediationAdRequest.getBirthday() != null || mediationAdRequest.getGender() !=
                -1 || mediationAdRequest.getLocation() != null;
    }

    private static int getAge(Date birthday) {
        int givenYear = Integer.parseInt((String) DateFormat.format("yyyy", birthday));
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);

        return currentYear - givenYear;
    }

    private class MBannerListener implements MoPubView.BannerAdListener {
        private MediationBannerListener mMediationBannerListener;

        MBannerListener(MediationBannerListener bannerListener) {
            mMediationBannerListener = bannerListener;
        }

        @Override
        public void onBannerClicked(MoPubView moPubView) {
            mMediationBannerListener.onAdClicked(MoPubAdapter.this);
            mMediationBannerListener.onAdLeftApplication(MoPubAdapter.this);
        }

        @Override
        public void onBannerCollapsed(MoPubView moPubView) {
            mMediationBannerListener.onAdClosed(MoPubAdapter.this);
        }

        @Override
        public void onBannerExpanded(MoPubView moPubView) {
            mMediationBannerListener.onAdOpened(MoPubAdapter.this);
        }

        @Override
        public void onBannerFailed(MoPubView moPubView,
                                   MoPubErrorCode moPubErrorCode) {
            try {
                switch (moPubErrorCode) {
                    case NO_FILL:
                        mMediationBannerListener.onAdFailedToLoad(MoPubAdapter.this,
                                AdRequest.ERROR_CODE_NO_FILL);
                        break;
                    case NETWORK_TIMEOUT:
                        mMediationBannerListener.onAdFailedToLoad(MoPubAdapter.this,
                                AdRequest.ERROR_CODE_NETWORK_ERROR);
                        break;
                    case SERVER_ERROR:
                        mMediationBannerListener.onAdFailedToLoad(MoPubAdapter.this,
                                AdRequest.ERROR_CODE_INVALID_REQUEST);
                        break;
                    default:
                        mMediationBannerListener.onAdFailedToLoad(MoPubAdapter.this,
                                AdRequest.ERROR_CODE_INTERNAL_ERROR);
                        break;
                }
            } catch (NoClassDefFoundError e) {
            }
        }

        @Override
        public void onBannerLoaded(MoPubView moPubView) {
            if (!(mAdSize.getWidth() == moPubView.getAdWidth()
                    && mAdSize.getHeight() == moPubView.getAdHeight())) {
                Log.e(TAG, "The banner ad size loaded does not match the request size. Update the"
                        + " ad size on your MoPub UI to match the request size.");
                mMediationBannerListener.onAdFailedToLoad(MoPubAdapter.this, AdRequest.ERROR_CODE_NO_FILL);
                return;
            }
            mMediationBannerListener.onAdLoaded(MoPubAdapter.this);

        }
    }

    @Override
    public void requestInterstitialAd(Context context,
                                      MediationInterstitialListener mediationInterstitialListener,
                                      Bundle bundle,
                                      MediationAdRequest mediationAdRequest,
                                      Bundle bundle1) {
        // Stub
    }

    @Override
    public void showInterstitial() {
        // Stub
    }
}
