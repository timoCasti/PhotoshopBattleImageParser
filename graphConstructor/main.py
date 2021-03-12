import csv
import hashlib
import os
import sys

import seaborn as sns
from collections import Counter
import matplotlib.pyplot as plt
import requests
from matplotlib.pyplot import show


def checkExistence(url, checksum):
    check = True

    try:
        img_data = requests.get(url).content
        m = hashlib.sha256()
        m.update(img_data)
        hashImage = m.hexdigest()
    except:
        print(" could not access url")
        check = False
        hashImage = "0"

    if not hashImage == checksum:
        print("hash :  " + hashImage + "  does not equal checksum from tsv : " + checksum)
        check = False

    return check


if __name__ == '__main__':

    doCheck = sys.argv[1]  # program argument which decides if images get checked if they still exist

    if not (os.path.exists("originals.tsv")):
        print("originals.tsv does not exist")
        exit(127)
    if not os.path.exists("photoshops.tsv"):
        print("photoshops.tsv does not exist")
        exit(127)

    fileOriginal = open("originals.tsv")
    fileOriginalChecked = open("originals_checked.tsv", "w", newline='')
    writer = csv.writer(fileOriginalChecked, delimiter="\t")
    readOriginal = csv.reader(fileOriginal, delimiter="\t")
    rowCounter = 0
    derivates = Counter()
    height = list()
    width = list()
    jpg = list()
    png = list()
    domain = Counter()
    deletedImages = 0
    foundderi = 0
    sizePsChecked = 0

    heightMax=0
    heightMin=1000000
    widthMax=0
    widthMin=1000000
    sizeOfDataset=0
    maxDerivates=0

    rowPS = 0
    rowOG = 0
    noKeyCounter = 0
    doubleKeysOriginal = list()
    noOriginalKey = list()

    for row in readOriginal:
        if rowCounter == 0:  # skip header
            writer.writerow(row)
            rowCounter = 1
            continue

        url = row[1]
        hash256 = row[3]

        if doCheck == "1":  # if check is enabled checks if image still exist, if not gets skipped
            if not checkExistence(url, hash256):
                deletedImages += 1
                continue

        # row= id	url	end	hash	filesize	score	author	link	timestamp	width	height

        # derivates id : count
        if row[0] in derivates:
            doubleKeysOriginal.append(row[0])
        else:
            derivates[row[0]] = 0

        # width and height in 2 differnt lists
        height.append(int(row[10]))
        width.append(int(row[9]))
        if int(row[10])>heightMax:
            heightMax=int(row[10])
        if int(row[10])<heightMin:
            heightMin=int(row[10])
        if int(row[9])>widthMax:
            widthMax=int(row[9])
        if int(row[9])<widthMin:
            widthMin=int(row[9])



        # image size
        if row[1].endswith("gif"):
            continue

        if row[2] == "jpeg" or row[2] == "jpg":
            jpg.append(int(row[4]) / 1000000)
            sizeOfDataset+=int(row[4]) / 1000000
        elif row[2] == "png":
            png.append(int(row[4]) / 1000000)
            sizeOfDataset += int(row[4]) / 1000000
        else:
            continue

        # domaint (imgur, reddit..)

        if "imgur" in row[1]:
            domain["imgur"] += 1
        elif "redd.it" in row[1]:
            domain["redd.it"] += 1
        elif "reddituploads" in row[1]:
            domain["reddituploads.com"] += 1

        else:
            domain["other"] += 1
            continue

        writer.writerow(row)

    fileOriginal.close()
    fileOriginalChecked.close()

    # load photoshops.tsv
    filePhotoshops = open("photoshops.tsv")
    filePhotoshopsChecked = open("photoshops_checked.tsv", "w", newline='')
    writer = csv.writer(filePhotoshopsChecked, delimiter="\t")
    readPhotoshops = csv.reader(filePhotoshops, delimiter="\t")

    for row in readPhotoshops:
        if rowCounter == 0:  # skip header
            writer.writerow(row)
            rowCounter = 1
            continue

        url = row[1]
        hash256 = row[3]

        if doCheck == 1:  # if check is enabled checks if image still exist, if not gets skipped
            print("Existence check enabled")
            if checkExistence(url, hash256) == 1:
                deletedImages += 1
                continue

        # row=id	original	url	end	hash	filesize	score	author	link	timestamp	width	height

        # derivates id : count
        if row[1] in derivates:

            foundderi += 1
            derivates[row[1]] += 1
        else:
            noKeyCounter += 1
            noOriginalKey.append(row[1])
            continue

        # width and height in 2 differnt lists
        height.append(int(row[11]))
        width.append(int(row[10]))
        if int(row[11])>heightMax:
            heightMax=int(row[11])
        if int(row[11])<heightMin:
            heightMin=int(row[11])
        if int(row[10])>widthMax:
            widthMax=int(row[10])
        if int(row[10])<widthMin:
            widthMin=int(row[10])


        # image size
        if row[2].endswith("gif"):
            continue

        if row[3] == "jpeg" or row[3] == "jpg":
            jpg.append(int(row[5]) / 1000000)
            sizeOfDataset += int(row[5]) / 1000000
        elif row[3] == "png":
            png.append(int(row[5]) / 1000000)
            sizeOfDataset += int(row[5]) / 1000000
        else:
            continue

            # domain (imgur, reddit..)

        if "imgur" in row[2]:
            domain["imgur"] += 1
        elif "redd.it" in row[2]:
            domain["redd.it"] += 1
        elif "reddituploads" in row[2]:
            domain["reddituploads.com"] += 1

        else:
            domain["other"] += 1
            continue

        sizePsChecked += 1
        writer.writerow(row)
        rowPS += 1

    fileOriginal.close()
    fileOriginalChecked.close()

    #filter originals without photoshops

    fileOriginal2 = open("originals_checked.tsv")
    fileOriginalChecked2 = open("originals_checked2.tsv", "w", newline='')
    writer = csv.writer(fileOriginalChecked2, delimiter="\t")
    readOriginal2 = csv.reader(fileOriginal2, delimiter="\t")
    rowCounter=0

    numberOfPhotoshops=list()

    for row in readOriginal2:
        if rowCounter == 0:  # skip header
            writer.writerow(row)
            rowCounter = 1
            continue

        if derivates[row[0]]==0:
            continue

        numberOfPhotoshops.append(derivates[row[0]])
        writer.writerow(row)
        rowOG += 1

    fileOriginal2.close()
    fileOriginalChecked2.close()




    print("doubleKeysOriginal  ", doubleKeysOriginal)
    print(" noOriginalKey  ", noOriginalKey.__len__())

    print("rowPS ", rowPS)
    print("rowog ", rowOG)
    print("Number of images ", rowOG+rowPS)

    print("# Deleted Images  : ", deletedImages)
    print("sizeOfDataset MB: " , sizeOfDataset)
    print("widthMin-Pixel: ",widthMin)
    print("widthMax: ",widthMax)
    print("heightMin: ", heightMin)
    print("heightMax", heightMax)

    # do plotting

    if sys.argv[2] == "wh":
        sns.set_style('darkgrid')
        fig, ax = plt.subplots()
        sns.kdeplot(data=width, multiple="layer", fill=True, ax=ax, log_scale=True, bw_adjust=.5)
        sns.kdeplot(data=height, multiple="layer", fill="true", bw_adjust=.5)
        ax.set_xticks([100, 200, 500, 1000, 2000, 5000, 10000, 20000])
        ax.set_xticklabels([100, 200, 500, 1000, 2000, 5000, 10000, 20000])
        ax.set_xlabel("Pixel ")
        ax.legend(("width", "height"))
        show()

    if sys.argv[2] == "derivates":
        sns.set_style('darkgrid')
        fig, ax = plt.subplots()

        c = dict(derivates)
        l = c.values()
        x = range(0,70)

        counterZero = 0
        adder=0
        zeroPSlist= list()
        for k,v in c.items():
            adder += v
            if v>maxDerivates:
                maxDerivates=v

            if v ==0:
                counterZero+=1
                zeroPSlist.append(k)



        #print(c.keys())
        print("counterZero", counterZero)
        print("avg  ", adder/rowOG)
        print("avg no zero  ", adder/(rowOG-counterZero))
        print("max Derivates : ", maxDerivates)

        listComp= set(noOriginalKey)&set(zeroPSlist)
        print("listComp ", listComp)

        plotCounter=Counter(numberOfPhotoshops)
        sns.lineplot(y=plotCounter.values(),x=plotCounter.keys(), marker="o", color="black",mfc="red")
        ax.set_xticks([0, 20, 40, 60,80,100])
        ax.set_yscale("log")
        ax.set_xticklabels([0, 20, 40, 60,80,100])
        ax.set_yticks([1, 5, 10, 50, 100, 500, 1000])
        ax.set_yticklabels([1, 5, 10, 50, 100, 500, 1000])

        ax.set_xlabel("Number of Derivates")
        ax.set_ylabel("Posts")
        show()

    if sys.argv[2] == "size":
        sns.set_style('darkgrid')
        fig, ax = plt.subplots()
        sns.kdeplot(data=jpg, multiple="layer", fill=True, ax=ax, log_scale=True, bw_adjust=.5)
        sns.kdeplot(data=png, multiple="layer", fill="true", bw_adjust=.5)
        ax.set_xticks([0.1,0.2,0.5,1.0,2.0,5.0,10.0,20.0])
        ax.set_xticklabels([0.1,0.2,0.5,1.0,2.0,5.0,10.0,20.0])
        ax.set_xlabel("Filesize in MB ")
        ax.legend(("jpg", "png"))
        show()

    if sys.argv[2] == "domain":

        print("domain ", domain)
        sns.set_style('darkgrid')
        fig, ax = plt.subplots()
        keys=["imgur.com", "redd.it","reddituploads.com"]
        values=[domain["imgur"],domain["redd.it"],domain["reddituploads.com"]]
        sns.barplot(x=keys,y=values,ax=ax,palette="mako")
        ax.set_yscale("log")
        ax.set_yticks([1,10,100,1000,10000,1000000])
        ax.set_yticklabels([1,10,100,1000,"10.000","1.000.000"])
        ax.set_ylabel("Count", labelpad=-5)
        show()