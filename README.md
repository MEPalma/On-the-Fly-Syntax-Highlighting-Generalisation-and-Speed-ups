# On-the-Fly Syntax Highlighting:\\ Generalisation and Speed-ups

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.14162905.svg)](https://doi.org/10.5281/zenodo.14162905)
[![CC BY 4.0](https://img.shields.io/badge/license-CC%20BY--NC%204.0-lightgrey.svg)](http://creativecommons.org/licenses/by-nc/4.0/)

> **On-the-Fly Syntax Highlighting:\\ Generalisation and Speed-ups
>
> On-the-fly syntax highlighting involves the rapid association of visual secondary notation with each character of a language derivation.
This task has grown in importance due to the widespread use of online software development tools, which frequently display source code
and heavily rely on efficient syntax highlighting mechanisms.
In this context, resolvers must address three key demands: speed, accuracy, and development costs.
Speed constraints are crucial for ensuring usability, providing responsive feedback for end users and minimizing system overhead.
At the same time, precise syntax highlighting is essential for improving code comprehension.
Achieving such accuracy, however, requires the ability to perform grammatical analysis, even in cases of varying correctness.
Additionally, the development costs associated with supporting multiple programming languages pose a significant challenge.
The technical challenges in balancing these three aspects explain why developers today experience significantly worse code syntax highlighting online compared to what they have locally.
The current state-of-the-art relies on leveraging programming languages' original lexers and parsers to generate syntax highlighting oracles,
which are used to train base Recurrent Neural Network models.
However, questions of generalisation remain.
This paper addresses this gap by extending previous work validation dataset to six mainstream programming languages thus providing a more thorough evaluation.
In response to limitations related to evaluation performance and training costs, this work introduces a novel Convolutional Neural Network (CNN) based model,
specifically designed to mitigate these issues.
Furthermore, this work addresses an area previously unexplored performance gains when deploying such models on GPUs.
The evaluation demonstrates that the new CNN-based implementation is significantly faster than existing state-of-the-art methods,
while still delivering the same near-perfect accuracy.

In this replication package, we provide the implementation used in our work.

## :open_file_folder: Organisation

The repository is organised as follows:

* [`src/`](/src) contains the scripts to build and execute the models
* [`src/main/resources`](src/main/resources) is the folder from which the dataset is sourced
* [`src/main/python/highlighter/saved_models`](src/main/python/highlighter/saved_models) is the folder containing the collection of trained models
* [`src/main/python/highlighter/saved_model_losses`](src/main/python/highlighter/saved_models) is the folder containing the collection of training logs for each trained model
* [`treesitter_server/`](/treesitter_server) contains all the tree-sitter syntax highlighting resolvers used

## :pray: Credits

* [Marco Edoardo Palma](mailto:marcoepalma@ifi.uzh.ch) - University of Zurich, Switzerland
* [Alex Wolf](mailto:alex.wolf@ifi.uzh.ch) - University of Zurich, Switzerland
* [Pasquale Salza](mailto:salza@ifi.uzh.ch) - University of Zurich, Switzerland
* [Harald C. Gall](mailto:gall@ifi.uzh.ch) - University of Zurich, Switzerland

