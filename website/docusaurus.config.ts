import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config = {
  title: 'SyncFlow',
  tagline: 'Enterprise-grade data synchronization platform',
  favicon: 'img/favicon.ico',

  future: {
    v4: true,
  },

  url: 'https://syncflow.io',
  baseUrl: '/',

  onBrokenLinks: 'warn',
  onBrokenMarkdownLinks: 'warn',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          editUrl: 'https://github.com/syncflow/syncflow/edit/main/docs/',
          routeBasePath: '/',
        },
        blog: {
          showReadingTime: true,
          feedOptions: {
            type: ['rss', 'atom'],
            xslt: true,
          },
          editUrl: 'https://github.com/syncflow/syncflow/edit/main/docs/',
        },
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    image: 'img/syncflow-social-card.jpg',
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'SyncFlow',
      logo: {
        alt: 'SyncFlow Logo',
        src: 'img/logo.svg',
      },
      items: [
        {to: '/', label: 'Home', position: 'left'},
        {
          type: 'docSidebar',
          sidebarId: 'tutorialSidebar',
          position: 'left',
          label: 'Documentation',
        },
        {to: '/blog', label: 'Blog', position: 'left'},
        {
          type: 'dropdown',
          label: 'Resources',
          position: 'left',
          items: [
            {label: 'API Reference', to: '/api'},
            {label: 'CLI Reference', to: '/cli'},
            {label: 'SDKs', to: '/sdks'},
            {label: 'Terraform Provider', to: '/terraform'},
            {label: 'Operator', to: '/operator'},
          ],
        },
        {
          type: 'dropdown',
          label: 'Community',
          position: 'left',
          items: [
            {label: 'Discord', href: 'https://discord.gg/syncflow'},
            {label: 'GitHub', href: 'https://github.com/syncflow/syncflow'},
            {label: 'Twitter', href: 'https://twitter.com/syncflowio'},
          ],
        },
        {
          href: 'https://github.com/syncflow/syncflow',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Product',
          items: [
            {label: 'Pipelines', to: '/pipelines'},
            {label: 'Connectors', to: '/connectors'},
            {label: 'Workflows', to: '/workflows'},
            {label: 'AI Copilot', to: '/ai'},
          ],
        },
        {
          title: 'Developers',
          items: [
            {label: 'API Reference', to: '/api'},
            {label: 'SDKs', to: '/sdks'},
            {label: 'Terraform Provider', to: '/terraform'},
            {label: 'Kubernetes Operator', to: '/operator'},
            {label: 'Plugin SDK', to: '/sdk'},
          ],
        },
        {
          title: 'Community',
          items: [
            {label: 'Discord', href: 'https://discord.gg/syncflow'},
            {label: 'GitHub', href: 'https://github.com/syncflow/syncflow'},
            {label: 'Twitter', href: 'https://twitter.com/syncflowio'},
          ],
        },
        {
          title: 'Legal',
          items: [
            {label: 'Privacy', to: '/privacy'},
            {label: 'Terms', to: '/terms'},
            {label: 'Security', to: '/security'},
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} SyncFlow, Inc.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
    },
  } satisfies Preset.ThemeConfig,
};

export default config;