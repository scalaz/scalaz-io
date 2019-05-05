/**
 * Copyright (c) 2017-present, Facebook, Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// See https://docusaurus.io/docs/site-config for all the possible
// site configuration options.

// List of projects/orgs using your project for the users page.
const users = [
  /*
  {
    caption: 'User1',
    // You will need to prepend the image path with your baseUrl
    // if it is not '/', like: '/test-site/img/docusaurus.svg'.
    image: '/img/docusaurus.svg',
    infoLink: 'https://www.facebook.com',
    pinned: true,
  },
  */
];

const siteConfig = {
  title: 'scalaz-zio', // Title for your website.
  tagline: 'Type-safe, composable asynchronous and concurrent programming for Scala',
  url: 'https://scalaz.github.io',
  baseUrl: '/scalaz-zio/',

  // Used for publishing and more
  projectName: 'scalaz-zio',
  organizationName: 'scalaz',

  // For no header links in the top nav bar -> headerLinks: [],
  headerLinks: [
    { doc: 'overview/overview_index', label: 'Overview' },
    { doc: 'datatypes/datatypes_index', label: 'Data Types' },
    { doc: 'interop/interop_index', label: 'Interop' },
    { doc: 'usecases/usecases_index', label: 'Use Cases' },
    { doc: 'advanced/advanced_index', label: 'Advanced' },
  ],

  // If you have users set above, you add it here:
  users,

  /* path to images for header/footer */
  headerIcon: 'img/navbar_brand.png',
  footerIcon: 'img/navbar_brand.png',
  favicon: 'img/favicon.png',

  /* Colors for website */
  colors: {
    primaryColor: '#000000',
    secondaryColor: '#000000',
  },

  /* Custom fonts for website */
  /*
  fonts: {
    myFont: [
      "Times New Roman",
      "Serif"
    ],
    myOtherFont: [
      "-apple-system",
      "system-ui"
    ]
  },
  */

  // This copyright info is used in /core/Footer.js and blog RSS/Atom feeds.
  copyright: `Copyright © ${new Date().getFullYear()} ZIO Maintainers`,

  highlight: {
    // Highlight.js theme to use for syntax highlighting in code blocks.
    theme: 'default',
  },

  // Add custom scripts here that would be placed in <script> tags.
  scripts: ['https://buttons.github.io/buttons.js'],

  // On page navigation for the current documentation page.
  onPageNav: 'separate',
  // No .html extensions for paths.
  cleanUrl: true,

  // Open Graph and Twitter card images.
  // ogImage: 'img/docusaurus.png',
  // twitterImage: 'img/docusaurus.png',

  // Show documentation's last contributor's name.
  enableUpdateBy: true,

  // Show documentation's last update time.
  enableUpdateTime: true,

  // You may provide arbitrary config keys to be used as needed by your
  // template. For example, if you need your repo's URL...
  //   repoUrl: 'https://github.com/facebook/test-site',

  scrollToTop: true,
  scrollToTopOptions: {
    cornerOffset: 100,
  },

  customDocsPath: 'scalaz-zio-docs/target/mdoc',
};

module.exports = siteConfig;
