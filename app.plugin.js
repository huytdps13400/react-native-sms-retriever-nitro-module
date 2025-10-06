const { withAndroidManifest } = require('@expo/config-plugins');

const withSMSRetriever = (config) => {
  return withAndroidManifest(config, (config) => {
    const androidManifest = config.modResults;

    if (!androidManifest.manifest) {
      androidManifest.manifest = {};
    }

    if (!androidManifest.manifest['uses-permission']) {
      androidManifest.manifest['uses-permission'] = [];
    }

    const permissions = [
      'android.permission.RECEIVE_SMS',
      'android.permission.READ_SMS',
    ];

    permissions.forEach((permission) => {
      const hasPermission = androidManifest.manifest['uses-permission'].some(
        (p) => p.$['android:name'] === permission
      );

      if (!hasPermission) {
        androidManifest.manifest['uses-permission'].push({
          $: { 'android:name': permission },
        });
      }
    });

    return config;
  });
};

module.exports = withSMSRetriever;
