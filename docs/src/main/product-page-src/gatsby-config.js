/**
 * Configure your Gatsby site with this file.
 *
 * See: https://www.gatsbyjs.org/docs/gatsby-config/
 */

module.exports = {
  plugins: [
    {
      resolve: `gatsby-plugin-typescript`,
      options: {
        isTSX: true,
        allExtensions: true,
      },
    },
    // {
    //   resolve: `gatsby-plugin-typography`,
    //   options: {
    //     pathToConfigModule: `./typography`,
    //   },
    // },
  ],
  siteMetadata: {
    title: "Blue Brain Nexus",
    description:
      "The flexible, open-source knowledge graph built for data-driven science.",
    url: "https://bluebrainnexus.io", // No trailing slash allowed!
    siteURL: "https://bluebrainnexus.io", // No trailing slash allowed!
    image: "/img/Nexus-v1.4-slate.png", // Path to your image you placed in the 'static' folder
    twitterUsername: "@bluebrainnexus",
    emailCatchAPI:
      "https://script.google.com/macros/s/AKfycbzG21hsMSsWiPa5fDd6IbPzrfPvZKVf0Xy7eJ4RmxWh38VHJIQ/exec",
  },
}
