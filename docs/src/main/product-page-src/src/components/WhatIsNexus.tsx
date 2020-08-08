import * as React from "react"

const products = [
  {
    title: "Nexus Fusion",
    subtitle: "Enabling Data and Knowledge Discovery",
    description:
      "Fusion is our extensible web application. It hosts different apps to accommodate various use cases. It comes by default with Studios (where you work with data), Admin (for managing the Nexus instance), and will soon support Workflows to organise your data activities. It runs on top of the Delta web services, and integrates neatly with our Forge python framework.",
  },
  {
    title: "Nexus Delta",
    subtitle: "Managing Data and Knowledge Graph Lifecycles",
    description:
      "A scalable and secure service to store and leverage all your data, neatly organised in a Knowledge Graph.",
  },
  {
    title: "Nexus Forge",
    subtitle: "Building and Using Knowledge Graphs Made Easy.",
    description:
      "Fusion is our extensible web application. It hosts different apps to accommodate various use cases. It comes by default with Studios (where you work with data), Admin (for managing the Nexus instance), and will soon support Workflows to organise your data activities. It runs on top of the Delta web services, and integrates neatly with our Forge python framework.",
  },
]

const ShortProductDescription: React.FC<{
  title: string
  subtitle: string
  description: string
}> = ({ title, subtitle, description }) => {
  return (
    <div className="columns short-product-description alternating-orientation">
      <div className="column">
        <div className="placeholder"></div>
      </div>
      <div className="column">
        <h3 className="title">{title}</h3>
        <h4 className="subtitle">{subtitle}</h4>
        <p>{description}</p>
        <a href="">
          <button className="button">Read More</button>
        </a>
      </div>
    </div>
  )
}

export default function WhatIsNexus() {
  return (
    <section id="what-is-nexus">
      <div className="container">
        <div className="content centered">
          <h2>What is Nexus?</h2>
          <p className="subtitle">
            subtitle Blue Brain Nexus is an ecosystem that allows you to
            organize and better leverage your data through the use of a
            Knowledge Graph. In addition to the products listed here, you’ll
            find a rich ecosystem of libraries and tools.
          </p>
        </div>
        <div className="content">
          {products.map(product => (
            <ShortProductDescription {...product} key={product.title} />
          ))}
        </div>
      </div>
    </section>
  )
}
