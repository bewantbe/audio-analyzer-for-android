% GNU Octave script
warning ("off", "Octave:broadcast");

% CODATA 2014
c  = 299792458;
h  = 6.626070040e-34;  % Planck constant
kB = 1.38064852e-23;   % Boltzmann constant
c1 = 2 * h * c * c;
c2 = h * c / kB;
PlanksLaw = @(T, lambda) c1 ./ lambda.^5 ./ (exp(c2 ./ lambda ./ T) - 1);

T0 = 300;
T1 = 12000;
n_div = 256;

cmf = load('ciexyzjv.csv');
%T_s = fliplr(1 ./ linspace(1/T1, 1/T0, n_div));
%T_s = linspace(T0, T1, n_div);
T_s = linspace(sqrt(T0), sqrt(T1), n_div).^2;
rgb = zeros(length(T_s), 3);
for id_T = 1:length(T_s)
  rgb(id_T, :) = cmf(:, 2:4)' * PlanksLaw(T_s(id_T), cmf(:, 1)*1e-9);
end
rgb ./= rgb(:, 2);
rgb .*= 1 - atan(1 ./ (T_s' / 2000) .^ 2) / pi * 2;  % light adjust
rgb *= 0.7;

sRGB_M = [
 3.2406 -1.5372 -0.4986
-0.9689  1.8758  0.0415
 0.0557 -0.2040  1.0570];

rgb = rgb * sRGB_M';

rgb(rgb>1) = 1;
rgb(rgb<0) = 0;

figure(1);
subplot(2,1,1);
rgbplot(rgb, 'profile');
subplot(2,1,2);
rgbplot(rgb, 'composite');

% inv gamma
invCSRGB = @(cs) (cs/12.92).*(cs<=0.040449936) + (((cs+0.055)/1.055).^2.4) .* (1 - (cs<=0.040449936));
figure(2);
plot(1:length(T_s), invCSRGB(rgb) * [0.2126 0.7152 0.0722]');
title('Relative luminance');


cm_ex1 = hot(256);

figure(9);
subplot(2,1,1);
rgbplot(cm_ex1, 'profile');
subplot(2,1,2);
rgbplot(cm_ex1, 'composite');
title('hot');

figure(10);
plot(1:length(cm_ex1), invCSRGB(cm_ex1) * [0.2126 0.7152 0.0722]');
title('Relative luminance (hot)');

%{
% approximation
function [x y] = CIE_xy_Temp(T)
  % in CIE xy
  if (T < 4000)
      x = -0.2661239*1e9/T/T/T - 0.2343580*1e6/T/T + 0.8776956*1e3/T + 0.179910;
  else
      x = -3.0258469*1e9/T/T/T + 2.1070379*1e6/T/T + 0.2226347*1e3/T + 0.240390;
  end
  if (T < 2222)
      y = -1.1063814*x*x*x - 1.34811020*x*x + 2.18555832*x - 0.20219683;
  elseif (T < 4000)
      y = -0.9549476*x*x*x - 1.37418593*x*x + 2.09137015*x - 0.16748867;
  else
      y =  3.0817580*x*x*x - 5.87338670*x*x + 3.75112997*x - 0.37001483;
  end
endfunction

function [r,g,b] = rgb_temp(T, Y)
  [x y] = CIE_xy_Temp(T);
  X = Y * x / y;
  Z = Y * (1-x-y) / y;
  CSRGB = @(c) (12.92*c).*(c<=0.0031308) + (1.055*c^(1/2.4)-0.055) .* (1-(c<=0.0031308));
  r = CSRGB( 3.2406*X - 1.5372*Y - 0.4986*Z);
  g = CSRGB(-0.9689*X + 1.8758*Y + 0.0415*Z);
  b = CSRGB( 0.0557*X - 0.2040*Y + 1.0570*Z);
endfunction
%}
