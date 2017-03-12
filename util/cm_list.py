#!/usr/bin/env python

# Ref:
# Origin (mpl colormaps):
#   https://bids.github.io/colormap/
# Raw data:
#   https://github.com/BIDS/colormap/blob/master/colormaps.py

import csv
import numpy as np
#import matplotlib.pyplot as plt  # matplotlib ver >= 1.5
names = ['magma', 'inferno', 'plasma', 'viridis', 'blackbody_uniform']

for c_name in names:
#    cmap = np.array(plt.get_cmap(c_name).colors)  # if use matplotlib
    with open(c_name+'.csv', 'rb') as f:
        reader = csv.reader(f)
        cmap = np.array(list(reader)).astype(np.float)
    #Note: len(cmap) == 256
    if len(cmap)==256:
        cm = np.floor(cmap[::-1] * 255.99);
    else:
        cm = np.vstack((np.floor(cmap[::-1] * 255.99),[0,0,0]))
    cm[-1] = [0, 0, 0]      # make last block black
    s = ", ".join([("0x%06x" % (c[0] * 2**16 + c[1] * 2**8 + c[2])) for c in cm])
    s2 = '\n'.join([s[0+i:80+i] for i in range(0, len(s), 80)])
    print("static final int[] " + c_name + " = {\n" + s2 + "\n};\n")

