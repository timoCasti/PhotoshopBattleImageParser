# PhototshopBattleImageParser
Repository including three different Projects which allow to create and download the [PS-Battles dataset, an image collection for image manipulation detection](https://arxiv.org/abs/1804.04866).

# psbParser
The first part is a small java script which filters the Reddit comments and submissions from Pushshift (https://files.pushshift.io/reddit) so that only comments and submissions from the PhotoshopBattle subreddit are kept.
psbParser is a maven project which can be installed with:  
 *mvn install*  
this will create a jar file which can be run with one argument, represetning the json file to be filtered.

# tsvParser
The second script takes the output from the psbParser which includes comments and submissions from the Photoshopbattle subreddit.  
tsvParser processes the comments and submissions to find the images from the PhotoshopBattle.  
Finally it produces a tsv file which lists the url's of the images with some other attributes.  
tsvParser is again a maven projct written in java and can again be build with:  
*mvn install*  
the created jar file takes two arguments, the first is the file to be processed wich need to start with "RC" for comments or "RS" for submissions and the second argument is the location of the geckodriver which is used in this parser (https://github.com/mozilla/geckodriver/).

# imgDownloader
This is the final script which is written in python, it allow to download all images from the produced tsv files.  
The project needs two files in its folder an originals.tsv and a photoshops.tsv which can be retrieved from: https://github.com/dbisUnibas/ps-battles
Running the script:  
*python downloader.py*  
will create two subfolder originals/photoshops which will include all the images presented in the tsv files.

# Bibtex
```
@article{heller2018psBattles,
  author        = {Silvan Heller and Luca Rossetto and Heiko Schuldt},
  title         = {{The PS-Battles Dataset -- an Image Collection for Image Manipulation Detection}},
  journal       = {CoRR},
  volume        = {abs/1804.04866},
  year          = {2018},
  url           = {http://arxiv.org/abs/1804.04866},
  archivePrefix = {arXiv},
  eprint        = {1804.04866}
}
```
