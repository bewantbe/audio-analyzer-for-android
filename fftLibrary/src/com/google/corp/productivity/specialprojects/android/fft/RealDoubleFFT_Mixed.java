/* Copyright 2011 Google Inc.
 *
 *Licensed under the Apache License, Version 2.0 (the "License");
 *you may not use this file except in compliance with the License.
 *You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *Unless required by applicable law or agreed to in writing, software
 *distributed under the License is distributed on an "AS IS" BASIS,
 *WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *See the License for the specific language governing permissions and
 *limitations under the License.
 *
 * Derived from jffpack, by suhler@google.com.
 * 
 * jfftpack is a Java version of fftpack. jfftpack is based
 * on Paul N. Swarztraubre's Fortran code and Pekka Janhuen's
 * C code. It is developed as part of my official duties as
 * lead software engineer for SCUBA-2 FTS projects
 * (www.roe.ac.uk/ukatc/projects/scubatwo/)
 * 
 * The original fftpack was public domain, so jfftpack is public domain too.
 * @author Baoshe Zhang
 * @author Astronomical Instrument Group of University of Lethbridge.
 */
package com.google.corp.productivity.specialprojects.android.fft;

class RealDoubleFFT_Mixed {
  static final int[] ntryh= new int[] {4, 2, 3, 5};

  /*-------------------------------------------------
   radf2: Real FFT's forward processing of factor 2
  -------------------------------------------------*/
  void radf2(int ido, int l1, final double cc[], double ch[], 
             final double wtable[], int offset) {
    int     i, k, ic;
    double  ti2, tr2;
    int iw1;
    iw1 = offset;

    for(k=0; k<l1; k++) {
      ch[2*k*ido]=cc[k*ido]+cc[(k+l1)*ido];
      ch[(2*k+1)*ido+ido-1]=cc[k*ido]-cc[(k+l1)*ido];
    }
    if(ido<2) return;
    if(ido !=2) {
      for(k=0; k<l1; k++) {
        for(i=2; i<ido; i+=2) {
          ic=ido-i;
          tr2 = wtable[i-2+iw1]*cc[i-1+(k+l1)*ido]
                                   +wtable[i-1+iw1]*cc[i+(k+l1)*ido];
          ti2 = wtable[i-2+iw1]*cc[i+(k+l1)*ido]
                                   -wtable[i-1+iw1]*cc[i-1+(k+l1)*ido];
          ch[i+2*k*ido]=cc[i+k*ido]+ti2;
          ch[ic+(2*k+1)*ido]=ti2-cc[i+k*ido];
          ch[i-1+2*k*ido]=cc[i-1+k*ido]+tr2;
          ch[ic-1+(2*k+1)*ido]=cc[i-1+k*ido]-tr2;
        }
      }
      if(ido%2==1)return;
    }
    for(k=0; k<l1; k++) {
      ch[(2*k+1)*ido]=-cc[ido-1+(k+l1)*ido];
      ch[ido-1+2*k*ido]=cc[ido-1+k*ido];
    }
  } 

  /*-------------------------------------------------
   radf3: Real FFT's forward processing of factor 3 
  -------------------------------------------------*/
  void radf3(int ido, int l1, final double cc[], double ch[], 
             final double wtable[], int offset) {
    final double taur=-0.5D;
    final double taui=0.866025403784439D;
    int     i, k, ic;
    double  ci2, di2, di3, cr2, dr2, dr3, ti2, ti3, tr2, tr3;
    int iw1, iw2;
    iw1 = offset;
    iw2 = iw1 + ido;

    for(k=0; k<l1; k++) {
      cr2=cc[(k+l1)*ido]+cc[(k+2*l1)*ido];
      ch[3*k*ido]=cc[k*ido]+cr2;
      ch[(3*k+2)*ido]=taui*(cc[(k+l1*2)*ido]-cc[(k+l1)*ido]);
      ch[ido-1+(3*k+1)*ido]=cc[k*ido]+taur*cr2;
    }
    if(ido==1) return;
    for(k=0; k<l1; k++) {
      for(i=2; i<ido; i+=2) {
        ic=ido-i;
        dr2 = wtable[i-2+iw1]*cc[i-1+(k+l1)*ido]
                                 +wtable[i-1+iw1]*cc[i+(k+l1)*ido];
        di2 = wtable[i-2+iw1]*cc[i+(k+l1)*ido]
                                 -wtable[i-1+iw1]*cc[i-1+(k+l1)*ido];
        dr3 = wtable[i-2+iw2]*cc[i-1+(k+l1*2)*ido]
                                 +wtable[i-1+iw2]*cc[i+(k+l1*2)*ido];
        di3 = wtable[i-2+iw2]*cc[i+(k+l1*2)*ido]
                                 -wtable[i-1+iw2]*cc[i-1+(k+l1*2)*ido];
        cr2 = dr2+dr3;
        ci2 = di2+di3;
        ch[i-1+3*k*ido]=cc[i-1+k*ido]+cr2;
        ch[i+3*k*ido]=cc[i+k*ido]+ci2;
        tr2=cc[i-1+k*ido]+taur*cr2;
        ti2=cc[i+k*ido]+taur*ci2;
        tr3=taui*(di2-di3);
        ti3=taui*(dr3-dr2);
        ch[i-1+(3*k+2)*ido]=tr2+tr3;
        ch[ic-1+(3*k+1)*ido]=tr2-tr3;
        ch[i+(3*k+2)*ido]=ti2+ti3;
        ch[ic+(3*k+1)*ido]=ti3-ti2;
      }
    }
  } 

  /*-------------------------------------------------
   radf4: Real FFT's forward processing of factor 4
  -------------------------------------------------*/
  void radf4(int ido, int l1, final double cc[], double ch[], 
             final double wtable[], int offset) {
    final double hsqt2=0.7071067811865475D;
    int i, k, ic;
    double  ci2, ci3, ci4, cr2, cr3, cr4, ti1, ti2, ti3, ti4, tr1, tr2, tr3, tr4;
    int iw1, iw2, iw3;
    iw1 = offset;
    iw2 = offset + ido;
    iw3 = iw2 + ido;
    for(k=0; k<l1; k++) {
      tr1=cc[(k+l1)*ido]+cc[(k+3*l1)*ido];
      tr2=cc[k*ido]+cc[(k+2*l1)*ido];
      ch[4*k*ido]=tr1+tr2;
      ch[ido-1+(4*k+3)*ido]=tr2-tr1;
      ch[ido-1+(4*k+1)*ido]=cc[k*ido]-cc[(k+2*l1)*ido];
      ch[(4*k+2)*ido]=cc[(k+3*l1)*ido]-cc[(k+l1)*ido];
    }
    if(ido<2) return;
    if(ido !=2) {
      for(k=0; k<l1; k++) {
        for(i=2; i<ido; i+=2) {
          ic=ido-i;
          cr2 = wtable[i-2+iw1]*cc[i-1+(k+l1)*ido]
                                   +wtable[i-1+iw1]*cc[i+(k+l1)*ido];
          ci2 = wtable[i-2+iw1]*cc[i+(k+l1)*ido]
                                   -wtable[i-1+iw1]*cc[i-1+(k+l1)*ido];
          cr3 = wtable[i-2+iw2]*cc[i-1+(k+2*l1)*ido]
                                   +wtable[i-1+iw2]*cc[i+(k+2*l1)*ido];
          ci3 = wtable[i-2+iw2]*cc[i+(k+2*l1)*ido]
                                   -wtable[i-1+iw2]*cc[i-1+(k+2*l1)*ido];
          cr4 = wtable[i-2+iw3]*cc[i-1+(k+3*l1)*ido]
                                   +wtable[i-1+iw3]*cc[i+(k+3*l1)*ido];
          ci4 = wtable[i-2+iw3]*cc[i+(k+3*l1)*ido]
                                   -wtable[i-1+iw3]*cc[i-1+(k+3*l1)*ido];
          tr1=cr2+cr4;
          tr4=cr4-cr2;
          ti1=ci2+ci4;
          ti4=ci2-ci4;
          ti2=cc[i+k*ido]+ci3;
          ti3=cc[i+k*ido]-ci3;
          tr2=cc[i-1+k*ido]+cr3;
          tr3=cc[i-1+k*ido]-cr3;
          ch[i-1+4*k*ido]=tr1+tr2;
          ch[ic-1+(4*k+3)*ido]=tr2-tr1;
          ch[i+4*k*ido]=ti1+ti2;
          ch[ic+(4*k+3)*ido]=ti1-ti2;
          ch[i-1+(4*k+2)*ido]=ti4+tr3;
          ch[ic-1+(4*k+1)*ido]=tr3-ti4;
          ch[i+(4*k+2)*ido]=tr4+ti3;
          ch[ic+(4*k+1)*ido]=tr4-ti3;
        }
      }
      if(ido%2==1) return;
    }
    for(k=0; k<l1; k++) {
      ti1=-hsqt2*(cc[ido-1+(k+l1)*ido]+cc[ido-1+(k+3*l1)*ido]);
      tr1=hsqt2*(cc[ido-1+(k+l1)*ido]-cc[ido-1+(k+3*l1)*ido]);
      ch[ido-1+4*k*ido]=tr1+cc[ido-1+k*ido];
      ch[ido-1+(4*k+2)*ido]=cc[ido-1+k*ido]-tr1;
      ch[(4*k+1)*ido]=ti1-cc[ido-1+(k+2*l1)*ido];
      ch[(4*k+3)*ido]=ti1+cc[ido-1+(k+2*l1)*ido];
    }
  } 

  /*-------------------------------------------------
   radf5: Real FFT's forward processing of factor 5
  -------------------------------------------------*/
  void radf5(int ido, int l1, final double cc[], double ch[], 
             final double wtable[], int offset) {
    final double tr11=0.309016994374947D;
    final double ti11=0.951056516295154D;
    final double tr12=-0.809016994374947D;
    final double ti12=0.587785252292473D;
    int     i, k, ic;
    double  ci2, di2, ci4, ci5, di3, di4, di5, ci3, cr2, cr3, dr2, dr3,
    dr4, dr5, cr5, cr4, ti2, ti3, ti5, ti4, tr2, tr3, tr4, tr5;
    int iw1, iw2, iw3, iw4;
    iw1 = offset;
    iw2 = iw1 + ido;
    iw3 = iw2 + ido;
    iw4 = iw3 + ido;

    for(k=0; k<l1; k++) {
      cr2=cc[(k+4*l1)*ido]+cc[(k+l1)*ido];
      ci5=cc[(k+4*l1)*ido]-cc[(k+l1)*ido];
      cr3=cc[(k+3*l1)*ido]+cc[(k+2*l1)*ido];
      ci4=cc[(k+3*l1)*ido]-cc[(k+2*l1)*ido];
      ch[5*k*ido]=cc[k*ido]+cr2+cr3;
      ch[ido-1+(5*k+1)*ido]=cc[k*ido]+tr11*cr2+tr12*cr3;
      ch[(5*k+2)*ido]=ti11*ci5+ti12*ci4;
      ch[ido-1+(5*k+3)*ido]=cc[k*ido]+tr12*cr2+tr11*cr3;
      ch[(5*k+4)*ido]=ti12*ci5-ti11*ci4;
    }
    if(ido==1) return;
    for(k=0; k<l1;++k) {
      for(i=2; i<ido; i+=2) {
        ic=ido-i;
        dr2 = wtable[i-2+iw1]*cc[i-1+(k+l1)*ido]
                                 +wtable[i-1+iw1]*cc[i+(k+l1)*ido];
        di2 = wtable[i-2+iw1]*cc[i+(k+l1)*ido]
                                 -wtable[i-1+iw1]*cc[i-1+(k+l1)*ido];
        dr3 = wtable[i-2+iw2]*cc[i-1+(k+2*l1)*ido]
                                 +wtable[i-1+iw2]*cc[i+(k+2*l1)*ido];
        di3 = wtable[i-2+iw2]*cc[i+(k+2*l1)*ido]
                                 -wtable[i-1+iw2]*cc[i-1+(k+2*l1)*ido];
        dr4 = wtable[i-2+iw3]*cc[i-1+(k+3*l1)*ido]
                                 +wtable[i-1+iw3]*cc[i+(k+3*l1)*ido];
        di4 = wtable[i-2+iw3]*cc[i+(k+3*l1)*ido]
                                 -wtable[i-1+iw3]*cc[i-1+(k+3*l1)*ido];
        dr5 = wtable[i-2+iw4]*cc[i-1+(k+4*l1)*ido]
                                 +wtable[i-1+iw4]*cc[i+(k+4*l1)*ido];
        di5 = wtable[i-2+iw4]*cc[i+(k+4*l1)*ido]
                                 -wtable[i-1+iw4]*cc[i-1+(k+4*l1)*ido];
        cr2=dr2+dr5;
        ci5=dr5-dr2;
        cr5=di2-di5;
        ci2=di2+di5;
        cr3=dr3+dr4;
        ci4=dr4-dr3;
        cr4=di3-di4;
        ci3=di3+di4;
        ch[i-1+5*k*ido]=cc[i-1+k*ido]+cr2+cr3;
        ch[i+5*k*ido]=cc[i+k*ido]+ci2+ci3;
        tr2=cc[i-1+k*ido]+tr11*cr2+tr12*cr3;
        ti2=cc[i+k*ido]+tr11*ci2+tr12*ci3;
        tr3=cc[i-1+k*ido]+tr12*cr2+tr11*cr3;
        ti3=cc[i+k*ido]+tr12*ci2+tr11*ci3;
        tr5=ti11*cr5+ti12*cr4;
        ti5=ti11*ci5+ti12*ci4;
        tr4=ti12*cr5-ti11*cr4;
        ti4=ti12*ci5-ti11*ci4;
        ch[i-1+(5*k+2)*ido]=tr2+tr5;
        ch[ic-1+(5*k+1)*ido]=tr2-tr5;
        ch[i+(5*k+2)*ido]=ti2+ti5;
        ch[ic+(5*k+1)*ido]=ti5-ti2;
        ch[i-1+(5*k+4)*ido]=tr3+tr4;
        ch[ic-1+(5*k+3)*ido]=tr3-tr4;
        ch[i+(5*k+4)*ido]=ti3+ti4;
        ch[ic+(5*k+3)*ido]=ti4-ti3;
      }
    }
  } 

  /*---------------------------------------------------------
   radfg: Real FFT's forward processing of general factor
  --------------------------------------------------------*/
  void radfg(int ido, int ip, int l1, int idl1, double cc[], 
             double c1[], double c2[], double ch[], double ch2[], 
             final double wtable[], int offset) {
    final double twopi=2.0D*Math.PI; //6.28318530717959;
    int     idij, ipph, i, j, k, l, j2, ic, jc, lc, ik, is, nbd;
    double  dc2, ai1, ai2, ar1, ar2, ds2, dcp, arg, dsp, ar1h, ar2h;
    int iw1 = offset;

    arg=twopi / ip;
    dcp=Math.cos(arg);
    dsp=Math.sin(arg);
    ipph=(ip+1)/ 2;
    nbd=(ido-1)/ 2;
    if(ido !=1) {
      for(ik=0; ik<idl1; ik++) ch2[ik]=c2[ik];
      for(j=1; j<ip; j++)
        for(k=0; k<l1; k++)
          ch[(k+j*l1)*ido]=c1[(k+j*l1)*ido];
      if(nbd<=l1) {
        is=-ido;
        for(j=1; j<ip; j++) {
          is+=ido;
          idij=is-1;
          for(i=2; i<ido; i+=2) {
            idij+=2;
            for(k=0; k<l1; k++) {
              ch[i-1+(k+j*l1)*ido]=
                wtable[idij-1+iw1]*c1[i-1+(k+j*l1)*ido]
                                      +wtable[idij+iw1]*c1[i+(k+j*l1)*ido];
              ch[i+(k+j*l1)*ido]=
                wtable[idij-1+iw1]*c1[i+(k+j*l1)*ido]
                                      -wtable[idij+iw1]*c1[i-1+(k+j*l1)*ido];
            }
          }
        }
      } else {
        is=-ido;
        for(j=1; j<ip; j++) {
          is+=ido;
          for(k=0; k<l1; k++) {
            idij=is-1;
            for(i=2; i<ido; i+=2) {
              idij+=2;
              ch[i-1+(k+j*l1)*ido]=
                wtable[idij-1+iw1]*c1[i-1+(k+j*l1)*ido]
                                      +wtable[idij+iw1]*c1[i+(k+j*l1)*ido];
              ch[i+(k+j*l1)*ido]=
                wtable[idij-1+iw1]*c1[i+(k+j*l1)*ido]
                                      -wtable[idij+iw1]*c1[i-1+(k+j*l1)*ido];
            }
          }
        }
      }
      if(nbd>=l1) {
        for(j=1; j<ipph; j++) {
          jc=ip-j;
          for(k=0; k<l1; k++) {
            for(i=2; i<ido; i+=2) {
              c1[i-1+(k+j*l1)*ido]=ch[i-1+(k+j*l1)*ido]+ch[i-1+(k+jc*l1)*ido];
              c1[i-1+(k+jc*l1)*ido]=ch[i+(k+j*l1)*ido]-ch[i+(k+jc*l1)*ido];
              c1[i+(k+j*l1)*ido]=ch[i+(k+j*l1)*ido]+ch[i+(k+jc*l1)*ido];
              c1[i+(k+jc*l1)*ido]=ch[i-1+(k+jc*l1)*ido]-ch[i-1+(k+j*l1)*ido];
            }
          }
        }
      } else {
        for(j=1; j<ipph; j++) {
          jc=ip-j;
          for(i=2; i<ido; i+=2) {
            for(k=0; k<l1; k++) {
              c1[i-1+(k+j*l1)*ido]=
                ch[i-1+(k+j*l1)*ido]+ch[i-1+(k+jc*l1)*ido];
              c1[i-1+(k+jc*l1)*ido]=ch[i+(k+j*l1)*ido]-ch[i+(k+jc*l1)*ido];
              c1[i+(k+j*l1)*ido]=ch[i+(k+j*l1)*ido]+ch[i+(k+jc*l1)*ido];
              c1[i+(k+jc*l1)*ido]=ch[i-1+(k+jc*l1)*ido]-ch[i-1+(k+j*l1)*ido];
            }
          }
        }
      }
    } else {				
      for(ik=0; ik<idl1; ik++) c2[ik]=ch2[ik];
    }
    for(j=1; j<ipph; j++) {
      jc=ip-j;
      for(k=0; k<l1; k++) {
        c1[(k+j*l1)*ido]=ch[(k+j*l1)*ido]+ch[(k+jc*l1)*ido];
        c1[(k+jc*l1)*ido]=ch[(k+jc*l1)*ido]-ch[(k+j*l1)*ido];
      }
    }

    ar1=1;
    ai1=0;
    for(l=1; l<ipph; l++) {
      lc=ip-l;
      ar1h=dcp*ar1-dsp*ai1;
      ai1=dcp*ai1+dsp*ar1;
      ar1=ar1h;
      for(ik=0; ik<idl1; ik++) {
        ch2[ik+l*idl1]=c2[ik]+ar1*c2[ik+idl1];
        ch2[ik+lc*idl1]=ai1*c2[ik+(ip-1)*idl1];
      }
      dc2=ar1;
      ds2=ai1;
      ar2=ar1;
      ai2=ai1;
      for(j=2; j<ipph; j++) {
        jc=ip-j;
        ar2h=dc2*ar2-ds2*ai2;
        ai2=dc2*ai2+ds2*ar2;
        ar2=ar2h;
        for(ik=0; ik<idl1; ik++) {
          ch2[ik+l*idl1]+=ar2*c2[ik+j*idl1];
          ch2[ik+lc*idl1]+=ai2*c2[ik+jc*idl1];
        }
      }
    }
    for(j=1; j<ipph; j++)
      for(ik=0; ik<idl1; ik++)
        ch2[ik]+=c2[ik+j*idl1];

    if(ido>=l1) {
      for(k=0; k<l1; k++) {
        for(i=0; i<ido; i++) {
          cc[i+k*ip*ido]=ch[i+k*ido];
        }
      }
    } else {
      for(i=0; i<ido; i++) {
        for(k=0; k<l1; k++) {
          cc[i+k*ip*ido]=ch[i+k*ido];
        }
      }
    }
    for(j=1; j<ipph; j++) {
      jc=ip-j;
      j2=2*j;
      for(k=0; k<l1; k++) {
        cc[ido-1+(j2-1+k*ip)*ido]=ch[(k+j*l1)*ido];
        cc[(j2+k*ip)*ido]=ch[(k+jc*l1)*ido];
      }
    }
    if(ido==1) return;
    if(nbd>=l1) {
      for(j=1; j<ipph; j++) {
        jc=ip-j;
        j2=2*j;
        for(k=0; k<l1; k++) {
          for(i=2; i<ido; i+=2) {
            ic=ido-i;
            cc[i-1+(j2+k*ip)*ido]=ch[i-1+(k+j*l1)*ido]+ch[i-1+(k+jc*l1)*ido];
            cc[ic-1+(j2-1+k*ip)*ido]=ch[i-1+(k+j*l1)*ido]-ch[i-1+(k+jc*l1)*ido];
            cc[i+(j2+k*ip)*ido]=ch[i+(k+j*l1)*ido]+ch[i+(k+jc*l1)*ido];
            cc[ic+(j2-1+k*ip)*ido]=ch[i+(k+jc*l1)*ido]-ch[i+(k+j*l1)*ido];
          }
        }
      }
    } else {
      for(j=1; j<ipph; j++) {
        jc=ip-j;
        j2=2*j;
        for(i=2; i<ido; i+=2) {
          ic=ido-i;
          for(k=0; k<l1; k++) {
            cc[i-1+(j2+k*ip)*ido]=ch[i-1+(k+j*l1)*ido]+ch[i-1+(k+jc*l1)*ido];
            cc[ic-1+(j2-1+k*ip)*ido]=ch[i-1+(k+j*l1)*ido]-ch[i-1+(k+jc*l1)*ido];
            cc[i+(j2+k*ip)*ido]=ch[i+(k+j*l1)*ido]+ch[i+(k+jc*l1)*ido];
            cc[ic+(j2-1+k*ip)*ido]=ch[i+(k+jc*l1)*ido]-ch[i+(k+j*l1)*ido];
          }
        }
      }
    }
  } 

  /*---------------------------------------------------------
   rfftf1: further processing of Real forward FFT
  --------------------------------------------------------*/
  // NOTE: ch must be preallocated to size n
  void rfftf1(int n, double c[], final double wtable[], int offset, double[] ch) {
    int     i;
    int     k1, l1, l2, na, kh, nf, ip, iw, ido, idl1;

    System.arraycopy(wtable, offset, ch, 0, n);

    nf=(int)wtable[1+2*n+offset];
    na=1;
    l2=n;
    iw=n-1+n+offset;
    for(k1=1; k1<=nf;++k1) {
      kh=nf-k1;
      ip=(int)wtable[kh+2+2*n+offset];
      l1=l2 / ip;
      ido=n / l2;
      idl1=ido*l1;
      iw-=(ip-1)*ido;
      na=1-na;
      if(ip==4) {
        if(na==0) {
          radf4(ido, l1, c, ch, wtable, iw);
        } else {
          radf4(ido, l1, ch, c, wtable, iw); 
        }
      } else if(ip==2) {
        if(na==0) {
          radf2(ido, l1, c, ch, wtable, iw);
        } else {
          radf2(ido, l1, ch, c, wtable, iw);
        }
      } else if(ip==3) {
        if(na==0) {
          radf3(ido, l1, c, ch, wtable, iw);
        } else {
          radf3(ido, l1, ch, c, wtable, iw);
        }
      } else if(ip==5) {
        if(na==0) {
          radf5(ido, l1, c, ch, wtable, iw);
        } else {
          radf5(ido, l1, ch, c, wtable, iw);
        }
      } else {
        if(ido==1) na=1-na;
        if(na==0) {
          radfg(ido, ip, l1, idl1, c, c, c, ch, ch, wtable, iw);
          na=1;
        } else {
          radfg(ido, ip, l1, idl1, ch, ch, ch, c, c, wtable, iw);
          na=0;
        }
      }
      l2=l1;
    }
    if(na==1) return;
    for(i=0; i<n; i++) c[i]=ch[i];
  }

  /*---------------------------------------------------------
   rfftf: Real forward FFT
  --------------------------------------------------------*/
  void rfftf(int n, double r[], double wtable[], double[] ch) {
    if(n==1) return;
    rfftf1(n, r, wtable, 0, ch);
  } 	/*rfftf*/

  /*---------------------------------------------------------
   rffti1: further initialization of Real FFT
  --------------------------------------------------------*/
  void rffti1(int n, double wtable[], int offset) {

    final double twopi=2.0D*Math.PI;
    double  argh;
    int     ntry=0, i, j;
    double  argld;
    int     k1, l1, l2, ib;
    double  fi;
    int     ld, ii, nf, ip, nl, is, nq, nr;
    double  arg;
    int     ido, ipm;
    int     nfm1;

    nl=n;
    nf=0;
    j=0;

    factorize_loop:
      while(true) {
        ++j;
        if(j<=4)
          ntry=ntryh[j-1];
        else
          ntry+=2;
        do {
          nq=nl / ntry;
          nr=nl-ntry*nq;
          if(nr !=0) continue factorize_loop;
          ++nf;
          wtable[nf+1+2*n+offset]=ntry;

          nl=nq;
          if(ntry==2 && nf !=1) {
            for(i=2; i<=nf; i++) {
              ib=nf-i+2;
              wtable[ib+1+2*n+offset]=wtable[ib+2*n+offset];
            }
            wtable[2+2*n+offset]=2;
          }
        } while(nl !=1);
        break factorize_loop;
      }
    wtable[0+2*n+offset] = n;
    wtable[1+2*n+offset] = nf;
    argh=twopi /(n);
    is=0;
    nfm1=nf-1;
    l1=1;
    if(nfm1==0) return;
    for(k1=1; k1<=nfm1; k1++) {
      ip=(int)wtable[k1+1+2*n+offset];
      ld=0;
      l2=l1*ip;
      ido=n / l2;
      ipm=ip-1;
      for(j=1; j<=ipm;++j) {
        ld+=l1;
        i=is;
        argld=ld*argh;

        fi=0;
        for(ii=3; ii<=ido; ii+=2) {
          i+=2;
          fi+=1;
          arg=fi*argld;
          wtable[i-2+n+offset] = Math.cos(arg);
          wtable[i-1+n+offset] = Math.sin(arg);
        }
        is+=ido;
      }
      l1=l2;
    }
  } /*rffti1*/

  /*---------------------------------------------------------
   rffti: Initialization of Real FFT
  --------------------------------------------------------*/
  void rffti(int n, double wtable[])  /* length of wtable = 2*n + 15 */ {
    if(n==1) return;
    rffti1(n, wtable, 0);
  } /*rffti*/
}
