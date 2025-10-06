const path = require('path');
const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');

const root = path.resolve(__dirname, '..');
const pak = require('../package.json');

const modules = Object.keys({
  ...pak.peerDependencies,
});

const config = getDefaultConfig(__dirname);

const finalConfig = {
  ...config,
  watchFolders: [root],

  resolver: {
    ...config.resolver,
    sourceExts: [...config.resolver.sourceExts, 'mjs', 'cjs'],
    nodeModulesPaths: [path.resolve(__dirname, 'node_modules')],

    // Block parent node_modules to prevent duplicate React
    blockList: [
      ...modules.map(
        (m) =>
          new RegExp(`^${escape(path.join(root, 'node_modules', m))}\\/.*$`)
      ),
    ],
  },
};

module.exports = mergeConfig(config, finalConfig);
