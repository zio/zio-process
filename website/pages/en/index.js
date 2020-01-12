/**
 * Copyright (c) 2017-present, Facebook, Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const React = require('react');

const CompLibrary = require('../../core/CompLibrary.js');

const MarkdownBlock = CompLibrary.MarkdownBlock; /* Used to read markdown */
const Container = CompLibrary.Container;
const GridBlock = CompLibrary.GridBlock;

class HomeSplash extends React.Component {
    render() {
        const {siteConfig, language = ''} = this.props;
        const {baseUrl, docsUrl} = siteConfig;
        const docsPart = `${docsUrl ? `${docsUrl}/` : ''}`;
        const langPart = `${language ? `${language}/` : ''}`;
        const docUrl = doc => `${baseUrl}${docsPart}${langPart}${doc}`;

        const SplashContainer = props => (
            <div className="homeContainer">
                <div className="homeSplashFade">
                    <div className="wrapper homeWrapper">{props.children}</div>
                </div>
            </div>
        );

        const Logo = props => (
            <div className="projectLogo">
                <img src={props.img_src} alt="Project Logo"/>
            </div>
        );

        const ProjectTitle = () => (
            <h2 className="projectTitle">
                {siteConfig.title}
                <small>{siteConfig.tagline}</small>
            </h2>
        );

        const PromoSection = props => (
            <div className="section promoSection">
                <div className="promoRow">
                    <div className="pluginRowBlock">{props.children}</div>
                </div>
            </div>
        );

        const Button = props => (
            <div className="pluginWrapper buttonWrapper">
                <a className="button" href={props.href} target={props.target}>
                    {props.children}
                </a>
            </div>
        );

        return (
            <SplashContainer>
                <div className="inner">
                    <ProjectTitle siteConfig={siteConfig}/>
                    <PromoSection>
                        <Button href={docUrl('overview/overview_index')}>Overview</Button>
                        <Button href="https://github.com/zio/zio-process" target="_blank">GitHub</Button>
                    </PromoSection>
                </div>
            </SplashContainer>
        );
    }
}

class Index extends React.Component {
    render() {
        const {config: siteConfig, language = ''} = this.props;
        const {baseUrl} = siteConfig;

        const Block = props => (
            <Container
                padding={['bottom', 'top']}
                id={props.id}
                background={props.background}>
                <GridBlock
                    align="center"
                    contents={props.children}
                    layout={props.layout}
                />
            </Container>
        );

        const FeatureCallout = () => (
            <div
                className="productShowcaseSection paddingBottom"
                style={{textAlign: 'center'}}>
                <h2>Welcome to ZIO Process</h2>
                <MarkdownBlock>
                  _Purely functional command and process library based on ZIO._
                </MarkdownBlock>

                <MarkdownBlock>
                    ZIO Process provides a principled way to call out to external programs from within a ZIO application
                    while leveraging ZIO's capabilities like interruption and offloading blocking operations to a
                    separate thread pool. You don't need to worry about avoiding these common pitfalls as you would if
                    you were to use Java's `ProcessBuilder` or the `scala.sys.process` API since it already taken care
                    of for you.
                </MarkdownBlock>
              
                <MarkdownBlock>
                    ZIO Process is backed by ZIO Streams, enabling you to work with processes that output gigabytes of
                    data without worrying about exceeding memory constraints.
                </MarkdownBlock>
            </div>
        );

        const Features = () => (
            <Block layout="fourColumn">
                {[
                    {
                        content: 'Leverages ZIO to handle interruption and offload blocking operations',
                        image: `${baseUrl}img/undraw_abstract.svg`,
                        imageAlign: 'top',
                        title: 'Integrated',
                    },
                    {
                        content: 'Supports streaming via ZIO Streams',
                        image: `${baseUrl}img/undraw_to_the_moon.svg`,
                        imageAlign: 'top',
                        title: 'Streaming',
                    },
                    {
                      content: 'Built-in support for piping and other common process operations',
                      image: `${baseUrl}img/undraw_software_engineer.svg`,
                      imageAlign: 'top',
                      title: 'Convenient',
                    }
                ]}
            </Block>
        );

        return (
            <div>
                <HomeSplash siteConfig={siteConfig} language={language}/>
                <div className="mainContainer">
                    <Features/>
                    <FeatureCallout/>
                </div>
            </div>
        );
    }
}

module.exports = Index;
